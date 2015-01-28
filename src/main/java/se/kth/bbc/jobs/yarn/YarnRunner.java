package se.kth.bbc.jobs.yarn;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.protocolrecords.GetNewApplicationResponse;
import org.apache.hadoop.yarn.api.records.ApplicationId;
import org.apache.hadoop.yarn.api.records.ApplicationSubmissionContext;
import org.apache.hadoop.yarn.api.records.ContainerLaunchContext;
import org.apache.hadoop.yarn.api.records.LocalResource;
import org.apache.hadoop.yarn.api.records.LocalResourceType;
import org.apache.hadoop.yarn.api.records.LocalResourceVisibility;
import org.apache.hadoop.yarn.api.records.Resource;
import org.apache.hadoop.yarn.api.records.YarnApplicationState;
import org.apache.hadoop.yarn.client.api.YarnClient;
import org.apache.hadoop.yarn.client.api.YarnClientApplication;
import org.apache.hadoop.yarn.conf.YarnConfiguration;
import org.apache.hadoop.yarn.exceptions.YarnException;
import org.apache.hadoop.yarn.util.ConverterUtils;
import se.kth.bbc.jobs.CancellableJob;
import se.kth.bbc.lims.Constants;
import se.kth.bbc.lims.Utils;

/**
 *
 * @author stig
 */
public class YarnRunner implements Closeable, CancellableJob {

  private static final Logger logger = Logger.getLogger(YarnRunner.class.
          getName());
  public static final String APPID_PLACEHOLDER = "$APPID";
  private static final String APPID_REGEX = "\\$APPID";
  private static final String KEY_CLASSPATH = "CLASSPATH";

  private final YarnClient yarnClient;
  private final Configuration conf;
  private ApplicationId appId = null;

  private final String amJarLocalName;
  private final String amJarPath;
  private final String amQueue;
  private int amMemory;
  private int amVCores;
  private String appName;
  private final String amMainClass;
  private String amArgs;
  private final Map<String, String> amLocalResources;
  private final Map<String, String> amEnvironment;
  private String localResourcesBasePath;
  private String stdOutPath;
  private String stdErrPath;
  private final boolean shouldCopyAmJarToLocalResources;
  private final List<String> filesToBeCopied;
  private final boolean logPathsAreHdfs;

  //---------------------------------------------------------------------------
  //-------------- CORE METHOD: START APPLICATION MASTER ----------------------
  //---------------------------------------------------------------------------
  /**
   * Start the Yarn Application Master.
   * <p>
   * @return The received ApplicationId identifying the application.
   * @throws YarnException
   * @throws IOException Can occur upon opening and moving execution and input
   * files.
   */
  public ApplicationId startAppMaster() throws YarnException, IOException {
    logger.info("Starting application master.");

    //Get application id
    yarnClient.start();
    YarnClientApplication app = yarnClient.createApplication();
    GetNewApplicationResponse appResponse = app.getNewApplicationResponse();
    appId = appResponse.getApplicationId();
    //And replace all occurences of $APPID with the real id.
    fillInAppid(appId.toString());

    //Check resource requests and availabilities
    checkAmResourceRequest(appResponse);

    //Set application name and type
    ApplicationSubmissionContext appContext = app.
            getApplicationSubmissionContext();
    appContext.setApplicationName(appName);
    appContext.setApplicationType("Hops Yarn");

    //Add local resources to AM container
    Map<String, LocalResource> localResources = addAllToLocalResources();

    //Copy files to HDFS that are expected to be there
    copyAllToHDFS();

    //Set up environment
    Map<String, String> env = new HashMap<>();
    env.putAll(amEnvironment);
    setUpClassPath(env);

    //Set up commands
    List<String> commands = setUpCommands();

    //TODO: set up security tokens
    //Set up container launch context
    ContainerLaunchContext amContainer = ContainerLaunchContext.newInstance(
            localResources, env, commands, null, null, null);

    //Finally set up context
    appContext.setAMContainerSpec(amContainer); //container spec
    appContext.setResource(Resource.newInstance(amMemory, amVCores)); //resources
    appContext.setQueue(amQueue); //Queue

    //And submit
    logger.
            log(Level.INFO,
                    "Submitting application {0} to applications manager.", appId);
    yarnClient.submitApplication(appContext);

    return appId;
  }

  //---------------------------------------------------------------------------
  //------------------------- UTILITY METHODS ---------------------------------
  //---------------------------------------------------------------------------
  private void fillInAppid(String id) {
    localResourcesBasePath = localResourcesBasePath.replaceAll(APPID_REGEX, id);
    appName = appName.replaceAll(APPID_REGEX, id);
    amArgs = amArgs.replaceAll(APPID_REGEX, id);
    stdOutPath = stdOutPath.replaceAll(APPID_REGEX, id);
    stdErrPath = stdErrPath.replaceAll(APPID_REGEX, id);
    for (Entry<String, String> entry : amLocalResources.entrySet()) {
      entry.setValue(entry.getValue().replaceAll(APPID_REGEX, id));
    }
    //TODO: thread-safety?
    for (Entry<String, String> entry : amEnvironment.entrySet()) {
      entry.setValue(entry.getValue().replaceAll(APPID_REGEX, id));
    }
  }

  private void checkAmResourceRequest(GetNewApplicationResponse appResponse) {
    int maxMem = appResponse.getMaximumResourceCapability().getMemory();
    if (amMemory > maxMem) {
      logger.log(Level.WARNING,
              "AM memory specified above max threshold of cluster. Using max value. Specified: {0}, max: {1}",
              new Object[]{amMemory,
                maxMem});
      amMemory = maxMem;
    }
    int maxVcores = appResponse.getMaximumResourceCapability().getVirtualCores();
    if (amVCores > maxVcores) {
      logger.log(Level.WARNING,
              "AM vcores specified above max threshold of cluster. Using max value. Specified: {0}, max: {1}",
              new Object[]{amVCores,
                maxVcores});
      amVCores = maxVcores;
    }
  }

  private Map<String, LocalResource> addAllToLocalResources() throws IOException {
    Map<String, LocalResource> localResources = new HashMap<>();
    //If an AM jar has been specified: include that one
    if (shouldCopyAmJarToLocalResources && amJarLocalName != null
            && !amJarLocalName.isEmpty() && amJarPath != null
            && !amJarPath.isEmpty()) {
      amLocalResources.put(amJarLocalName, amJarPath);
    }
    FileSystem fs = FileSystem.get(conf);
    String hdfsPrefix = conf.get("fs.defaultFS");
    String basePath = hdfsPrefix + localResourcesBasePath;
    logger.log(Level.INFO, "Base path: {0}", basePath);
    for (Entry<String, String> entry : amLocalResources.entrySet()) {
      String key = entry.getKey();
      String source = entry.getValue();
      String filename = Utils.getFileName(source);
      Path dst = new Path(basePath + File.separator + filename);
      fs.copyFromLocalFile(new Path(source), dst);
      logger.log(Level.INFO, "Copying from: {0} to: {1}",
              new Object[]{source,
                dst});
      FileStatus scFileStat = fs.getFileStatus(dst);
      LocalResource scRsrc = LocalResource.newInstance(ConverterUtils.
              getYarnUrlFromPath(dst),
              LocalResourceType.FILE, LocalResourceVisibility.PUBLIC,
              scFileStat.getLen(),
              scFileStat.getModificationTime());
      localResources.put(key, scRsrc);
    }
    return localResources;
  }

  private void copyAllToHDFS() throws IOException {
    FileSystem fs = FileSystem.get(conf);
    String hdfsPrefix = conf.get("fs.defaultFS");
    String basePath = hdfsPrefix + localResourcesBasePath;
    for (String path : filesToBeCopied) {
      String destination = basePath + File.separator + Utils.getFileName(path);
      Path dst = new Path(destination);
      fs.copyFromLocalFile(new Path(path), dst);
    }
  }

  private void setUpClassPath(Map<String, String> env) {
    // Add AppMaster.jar location to classpath
    StringBuilder classPathEnv = new StringBuilder(
            ApplicationConstants.Environment.CLASSPATH.$())
            .append(":").append("./*");
    for (String c : conf.getStrings(
            YarnConfiguration.YARN_APPLICATION_CLASSPATH,
            YarnConfiguration.DEFAULT_YARN_APPLICATION_CLASSPATH)) {
      classPathEnv.append(":").append(c.trim());
    }
    classPathEnv.append(":").append("./log4j.properties");
    // add the runtime classpath needed for tests to work
    if (conf.getBoolean(YarnConfiguration.IS_MINI_YARN_CLUSTER, false)) {
      classPathEnv.append(':');
      classPathEnv.append(System.getProperty("java.class.path"));
    }
    //Check whether a classpath variable was already set, and if so: merge them
    //TODO: clean this up so no doubles are found in the classpath.
    if (env.containsKey(KEY_CLASSPATH)) {
      String clpth = env.get(KEY_CLASSPATH) + ":" + classPathEnv.toString();
      env.put(KEY_CLASSPATH, clpth);
    } else {
      env.put(KEY_CLASSPATH, classPathEnv.toString());
    }
  }

  private List<String> setUpCommands() {
    // Set the necessary command to execute the application master
    List<CharSequence> vargs = new ArrayList<>();
    // Set java executable command
    logger.info("Setting up app master command");
    vargs.add(ApplicationConstants.Environment.JAVA_HOME.$() + "/bin/java");
    // Set Xmx based on am memory size
    vargs.add("-Xmx" + amMemory + "M");
    // Set class name
    vargs.add(amMainClass);
    // Set params for Application Master
    vargs.add(amArgs);

    vargs.add("1> ");
    vargs.add(stdOutPath);

    vargs.add("2> ");
    vargs.add(stdErrPath);
    
    // Get final commmand
    StringBuilder amcommand = new StringBuilder();
    for (CharSequence str : vargs) {
      amcommand.append(str).append(" ");
    }
    logger.log(Level.INFO, "Completed setting up app master command: {0}",
            amcommand.toString());
    List<String> commands = new ArrayList<>();
    commands.add(amcommand.toString());
    return commands;
  }

  //---------------------------------------------------------------------------        
  //--------------------------- STATUS QUERIES --------------------------------
  //---------------------------------------------------------------------------
  public YarnApplicationState getApplicationState() throws YarnException,
          IOException {
    return yarnClient.getApplicationReport(appId).getYarnApplicationState();
  }

  //---------------------------------------------------------------------------        
  //------------------------- YARNCLIENT UTILS --------------------------------
  //---------------------------------------------------------------------------
  @Override
  public void close() throws IOException {
    if (yarnClient != null) {
      yarnClient.close();
    }
  }

  @Override
  public void cancelJob() throws YarnException, IOException {
    if (yarnClient != null) {
      yarnClient.close();
    } else {
      try (YarnClient tmpclient = YarnClient.createYarnClient()) {
        tmpclient.killApplication(appId);
      }
    }
  }

  //---------------------------------------------------------------------------        
  //------------------------- CONSTRUCTOR -------------------------------------
  //---------------------------------------------------------------------------
  public YarnRunner(Builder builder) {
    this.amJarLocalName = builder.amJarLocalName;
    this.amJarPath = builder.amJarPath;
    this.amQueue = builder.amQueue;
    this.amMemory = builder.amMemory;
    this.amVCores = builder.amVCores;
    this.appName = builder.appName;
    this.amMainClass = builder.amMainClass;
    this.amArgs = builder.amArgs;
    this.amLocalResources = builder.amLocalResources;
    this.amEnvironment = builder.amEnvironment;
    this.localResourcesBasePath = builder.localResourcesBasePath;
    this.yarnClient = builder.yarnClient;
    this.conf = builder.conf;
    this.shouldCopyAmJarToLocalResources
            = builder.shouldAddAmJarToLocalResources;
    this.filesToBeCopied = builder.filesToBeCopied;
    this.logPathsAreHdfs = builder.logPathsAreRelativeToResources;
    this.stdOutPath = builder.stdOutPath;
    this.stdErrPath = builder.stdErrPath;
  }

  //---------------------------------------------------------------------------
  //-------------------------- GETTERS ----------------------------------------
  //---------------------------------------------------------------------------
  public ApplicationId getAppId() {
    return appId;
  }

  public String getAmArgs() {
    return amArgs;
  }

  public String getLocalResourcesBasePath() {
    return localResourcesBasePath;
  }

  public String getStdOutPath() {
    if (logPathsAreHdfs) {
      return localResourcesBasePath + File.separator + stdOutPath;
    } else {
      return stdOutPath;
    }
  }

  public String getStdErrPath() {
    if (logPathsAreHdfs) {

      return localResourcesBasePath + File.separator + stdErrPath;
    } else {
      return stdErrPath;
    }
  }

  public boolean areLogPathsHdfs() {
    return logPathsAreHdfs;
  }
  //---------------------------------------------------------------------------
  //-------------------------- BUILDER ----------------------------------------
  //---------------------------------------------------------------------------

  public static class Builder {

    //Possibly equired attributes
    //The name of the application app master class
    private String amMainClass;
    //Path to the application master jar
    private String amJarPath;
    //The name of the application master jar in the local resources
    private String amJarLocalName;

    //Optional attributes
    // Queue for App master
    private String amQueue = "default"; //TODO: enable changing this, or infer from user data
    // Memory for App master (in MB)
    private int amMemory = 1024;
    //Number of cores for appMaster
    private int amVCores = 1;
    // Application name
    private String appName = "Hops Yarn";
    //Arguments to pass on in invocation of Application master
    private String amArgs;
    //List of paths to resources that should be copied to application master
    private Map<String, String> amLocalResources = null;
    //Application master environment
    private Map<String, String> amEnvironment = null;
    //Path where the application master expects its local resources to be (added to fs.getHomeDirectory)
    private String localResourcesBasePath;
    //Path to file where stdout should be written, default in tmp folder
    private String stdOutPath;
    //Path to file where stderr should be written, default in tmp folder
    private String stdErrPath;
    //Signify whether the log paths are relative to the localResourcesBasePath
    private boolean logPathsAreRelativeToResources = false;
    //Signify whether the application master jar should be added to local resources
    private boolean shouldAddAmJarToLocalResources = true;
    //List of files to be copied to localResourcesBasePath
    private List<String> filesToBeCopied = new ArrayList<>();

    //Hadoop Configuration
    private Configuration conf;
    //YarnClient
    private YarnClient yarnClient;

    //Constructors
    public Builder(String amMainClass) {
      this.amMainClass = amMainClass;
    }

    public Builder(String amJarPath, String amJarLocalName) {
      this.amJarPath = amJarPath;
      this.amJarLocalName = amJarLocalName;
    }

    //Setters
    /**
     * Sets the arguments to be passed to the Application Master.
     * <p>
     * @param amArgs
     * @return
     */
    public Builder amArgs(String amArgs) {
      this.amArgs = amArgs;
      return this;
    }

    public Builder amMemory(int amMem) {
      this.amMemory = amMem;
      return this;
    }

    public Builder amVCores(int amVCores) {
      this.amVCores = amVCores;
      return this;
    }

    public Builder appName(String appName) {
      this.appName = appName;
      return this;
    }

    public Builder amMainClass(String amMainClass) {
      this.amMainClass = amMainClass;
      return this;
    }

    public Builder amJar(String amJarPath, String amJarLocalName) {
      this.amJarLocalName = amJarLocalName;
      this.amJarPath = amJarPath;
      return this;
    }

    public Builder addAmJarToLocalResources(boolean value) {
      this.shouldAddAmJarToLocalResources = value;
      return this;
    }

    public Builder addFilePathToBeCopied(String path) {
      filesToBeCopied.add(path);
      return this;
    }

    /**
     * Sets the path to which to write the Application Master's stdout.
     * <p>
     * @param path
     * @return
     */
    public Builder stdOutPath(String path) {
      this.stdOutPath = path;
      return this;
    }

    /**
     * Sets the path to which to write the Application Master's stderr.
     * <p>
     * @param path
     * @return
     */
    public Builder stdErrPath(String path) {
      this.stdErrPath = path;
      return this;
    }

    public Builder logPathsRelativeToResourcesPath(boolean value) {
      this.logPathsAreRelativeToResources = value;
      return this;
    }

    /**
     * Set the base path for local resources for the application master.
     * This is the path where the AM expects its local resources to be. Use
     * "$APPID" as a replacement for the appId, which will be replaced once
     * it is available.
     * <p>
     * If this method is not invoked, a default path will be used.
     *
     * @param basePath
     * @return
     */
    public Builder localResourcesBasePath(String basePath) {
      while (basePath.endsWith(File.separator)) {
        basePath = basePath.substring(0, basePath.length() - 1);
      }
      //TODO: handle paths like "hdfs://"
      if (!basePath.startsWith("/")) {
        basePath = "/" + basePath;
      }
      this.localResourcesBasePath = basePath;
      return this;
    }

    /**
     * Add a local resource that should be added to the AM container. The
     * name is the key as used in the LocalResources map passed to the
     * container. The source is the local path to the file. The file will be
     * copied
     * into HDFS under the path <i>localResourcesBasePath</i>/<i>filename</i>.
     *
     * @param name The name of the local resource, key in the local resource
     * map.
     * @param source The local path to the file.
     * @return
     */
    public Builder addLocalResource(String name, String source) {
      if (amLocalResources == null) {
        amLocalResources = new HashMap<>();
      }
      amLocalResources.put(name, source);
      return this;
    }

    public Builder addToAppMasterEnvironment(String key, String value) {
      if (amEnvironment == null) {
        this.amEnvironment = new HashMap<>();
      }
      amEnvironment.put(key, value);
      return this;
    }

    public Builder addAllToAppMasterEnvironment(Map<String, String> env) {
      if (amEnvironment == null) {
        this.amEnvironment = new HashMap<>();
      }
      amEnvironment.putAll(env);
      return this;
    }

    /**
     * Build the YarnRunner instance
     * <p>
     * @return
     * @throws IllegalStateException Thrown if (a) configuration is not found,
     * (b) invalid main class name
     * @throws IOException Thrown if stdOut and/or stdErr path have not been set
     * and temp files could not be created
     */
    public YarnRunner build() throws IllegalStateException, IOException {
      //Set configuration
      try {
        setConfiguration();
      } catch (IllegalStateException e) {
        throw new IllegalStateException("Failed to load configuration", e);
      }

      //Set YarnClient
      yarnClient = YarnClient.createYarnClient();
      yarnClient.init(conf);

      //Set main class
      if (amMainClass == null) {
        amMainClass = getMainClassNameFromJar();
        if (amMainClass == null) {
          throw new IllegalStateException(
                  "Could not infer main class name from jar and was not specified.");
        }
      }
      //Default localResourcesBasePath
      if (localResourcesBasePath == null) {
        localResourcesBasePath = appName + File.separator + APPID_PLACEHOLDER;
      }
      //Default log locations: tmp files
      if (stdOutPath == null || stdOutPath.isEmpty()) {
        try {
          stdOutPath = Files.createTempFile("stdOut", "").toString();
        } catch (IOException e) {
          throw new IOException("Failed to create tmp log file.", e);
        }
      }
      if (stdErrPath == null || stdErrPath.isEmpty()) {
        try {
          stdErrPath = Files.createTempFile("stdErr", "").toString();
        } catch (IOException e) {
          throw new IOException("Failed to create tmp log file.", e);
        }
      }

      if (amLocalResources == null) {
        amLocalResources = new HashMap<>();
      }

      if (amEnvironment == null) {
        amEnvironment = new HashMap<>();
      }

      return new YarnRunner(this);
    }

    private void setConfiguration() throws IllegalStateException {
      //Get the path to the Yarn configuration file from environment variables
      String yarnConfDir = System.getenv(Constants.ENV_KEY_YARN_CONF_DIR);
      //If not found in environment variables: warn and use default
      if (yarnConfDir == null) {
        logger.log(Level.WARNING,
                "Environment variable " + Constants.ENV_KEY_YARN_CONF_DIR
                + " not found, using default "
                + Constants.DEFAULT_YARN_CONF_DIR);
        yarnConfDir = Constants.DEFAULT_YARN_CONF_DIR;
      }

      //Get the configuration file at found path
      Path confPath = new Path(yarnConfDir);
      File confFile = new File(confPath + File.separator
              + Constants.DEFAULT_YARN_CONFFILE_NAME);
      if (!confFile.exists()) {
        logger.log(Level.SEVERE,
                "Unable to locate Yarn configuration file in {0}. Aborting exectution.",
                confFile);
        throw new IllegalStateException("No Yarn conf file");
      }

      //Also add the hadoop config
      String hadoopConfDir = System.getenv(Constants.ENV_KEY_HADOOP_CONF_DIR);
      //If not found in environment variables: warn and use default
      if (hadoopConfDir == null) {
        logger.log(Level.WARNING,
                "Environment variable " + Constants.ENV_KEY_HADOOP_CONF_DIR
                + " not found, using default "
                + Constants.DEFAULT_HADOOP_CONF_DIR);
        hadoopConfDir = Constants.DEFAULT_HADOOP_CONF_DIR;
      }
      confPath = new Path(hadoopConfDir);
      File hadoopConf = new File(confPath + File.separator
              + Constants.DEFAULT_HADOOP_CONFFILE_NAME);
      if (!hadoopConf.exists()) {
        logger.log(Level.SEVERE,
                "Unable to locate Hadoop configuration file in {0}. Aborting exectution.",
                hadoopConf);
        throw new IllegalStateException("No Hadoop conf file");
      }

      //And the hdfs config
      File hdfsConf = new File(confPath + File.separator
              + Constants.DEFAULT_HDFS_CONFFILE_NAME);
      if (!hdfsConf.exists()) {
        logger.log(Level.SEVERE,
                "Unable to locate HDFS configuration file in {0}. Aborting exectution.",
                hdfsConf);
        throw new IllegalStateException("No HDFS conf file");
      }

      //Set the Configuration object for the returned YarnClient
      conf = new Configuration();
      conf.addResource(new Path(confFile.getAbsolutePath()));
      conf.addResource(new Path(hadoopConf.getAbsolutePath()));
      conf.addResource(new Path(hdfsConf.getAbsolutePath()));

      addPathToConfig(conf, confFile);
      addPathToConfig(conf, hadoopConf);
      setDefaultConfValues(conf);
    }

    private static void addPathToConfig(Configuration conf, File path) {
      // chain-in a new classloader
      URL fileUrl = null;
      try {
        fileUrl = path.toURL();
      } catch (MalformedURLException e) {
        throw new RuntimeException("Erroneous config file path", e);
      }
      URL[] urls = {fileUrl};
      ClassLoader cl = new URLClassLoader(urls, conf.getClassLoader());
      conf.setClassLoader(cl);
    }

    private static void setDefaultConfValues(Configuration conf) {
      if (conf.get("fs.hdfs.impl", null) == null) {
        conf.set("fs.hdfs.impl", "org.apache.hadoop.hdfs.DistributedFileSystem");
      }
      if (conf.get("fs.file.impl", null) == null) {
        conf.set("fs.file.impl", "org.apache.hadoop.fs.LocalFileSystem");
      }
    }

    private String getMainClassNameFromJar() {
      if (amJarPath == null) {
        throw new IllegalStateException(
                "Main class name and amJar path cannot both be null.");
      }
      String fileName = amJarPath;
      String mainClassName = null;

      try (JarFile jarFile = new JarFile(fileName)) {
        Manifest manifest = jarFile.getManifest();
        if (manifest != null) {
          mainClassName = manifest.getMainAttributes().getValue("Main-Class");
        }
      } catch (IOException io) {
        logger.log(Level.SEVERE, "Could not open jar file " + amJarPath
                + " to load main class.", io);
        return null;
      }

      if (mainClassName != null) {
        return mainClassName.replaceAll("/", ".");
      } else {
        return null;
      }
    }

  }

}