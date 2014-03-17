/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package se.kth.hop.deploy.baremetal;

import java.io.IOException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.common.IOUtils;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;
import net.schmizz.sshj.transport.verification.PromiscuousVerifier;
import net.schmizz.sshj.userauth.keyprovider.KeyProvider;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.domain.NodeMetadataBuilder;
import org.jclouds.domain.Location;
import org.jclouds.domain.LocationBuilder;
import org.jclouds.domain.LocationScope;
import org.jclouds.domain.LoginCredentials;
import org.jclouds.scriptbuilder.domain.OsFamily;
import se.kth.hop.deploy.provision.MessageController;
import se.kth.hop.deploy.provision.ScriptBuilder;
import se.kth.hop.deploy.virtualization.parser.BaremetalGroup;

/**
 * Asynchronous Thread that handles SSH session to the node and executes the 
 * given initialization script.
 * 
 *  It returns back the ExecResponse which wraps the result of executing the SSH session.
 * 
 * @author Alberto Lorente Leal <albll@kth.se>
 */

public class InitializeBaremetalCallable implements Callable<Set<NodeMetadata>>{
    private SSHClient client;
    private KeyProvider keys;
    private String privateKey;
    private String host;
    private String loginUser;
    private ScriptBuilder initScript;
    private Map<String, Set<? extends NodeMetadata>> nodes;
    private BaremetalGroup group;
    private MessageController messages;

    public InitializeBaremetalCallable(String privateKey, String host, String loginUser, 
            ScriptBuilder initScript, Map<String, Set<? extends NodeMetadata>> nodes, 
            BaremetalGroup group, MessageController messages) {
        this.privateKey = privateKey;
        this.host = host;
        this.loginUser = loginUser;
        this.initScript = initScript;
        this.nodes = nodes;
        this.group = group;
        this.messages = messages;
    }
    
    
    
    @Override
    public Set<NodeMetadata> call() throws Exception {
        Set<NodeMetadata> ready = new HashSet<NodeMetadata>();
                        client = new SSHClient();
                        client.addHostKeyVerifier(new PromiscuousVerifier());
                        try {
                            keys = client.loadKeys(privateKey, null, null);

                            client.connect(host);
                            client.authPublickey(loginUser, keys);
                            final Session session = client.startSession();
                            System.out.println("Starting SSH session on Host: "+host);
                            final Command cmd = session.exec(initScript.render(OsFamily.UNIX));
                            System.out.println(IOUtils.readFully(cmd.getInputStream()).toString());
                            System.out.println("\n** exit status: " + cmd.getExitStatus());
                            session.close();
                            client.disconnect();
                            NodeMetadataBuilder builder = new NodeMetadataBuilder();
                            Set<String> publicIP = new HashSet<String>();
                            publicIP.add(host);
                            LoginCredentials credentials = LoginCredentials.builder().user(loginUser)
                                    .privateKey(privateKey).noPassword().build();
                            Location location = new LocationBuilder().id("KTHFS")
                                    .description("RandomRegion").scope(LocationScope.HOST).build();
                            NodeMetadata node = builder.hostname(host).id(host).location(location)
                                    .loginPort(22)
                                    .name("BareMetalNode").credentials(credentials).ids(host).providerId("Physical")
                                    .status(NodeMetadata.Status.RUNNING)
                                    .publicAddresses(publicIP).privateAddresses(publicIP).build();
                            ready.add(node);


                        } catch (IOException e) {
                            e.printStackTrace();
                            System.out.println("Error loading the private Key");
                        } finally {
                            nodes.put(group.getServices().get(0), ready);
                            messages.addMessage("Nodes created in Security Group " + group.getServices().get(0) + " with "
                                    + "basic setup");
                            return ready;
                        }
                    }
    }
    
