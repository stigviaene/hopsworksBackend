package se.kth.bbc.security.auth;

import java.io.Serializable;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.ejb.EJB;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.RequestScoped;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.mail.MessagingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import se.kth.bbc.lims.MessagesController;
import se.kth.bbc.security.ua.EmailBean;
import se.kth.bbc.security.ua.PeopleAccountStatus;
import se.kth.bbc.security.ua.UserAccountsEmailMessages;
import se.kth.bbc.security.ua.UserManager;
import se.kth.bbc.security.ua.model.User;

/**
 *
 * @author Ali Gholami <gholami@pdc.kth.se>
 */
@ManagedBean
@RequestScoped
public class CustomAuthentication implements Serializable {

  private static final long serialVersionUID = 1L;

  // Issuer of the QrCode
  public static final String ISSUER = "BiobankCloud";

  // To distinguish Yubikey users
  private final String YUBIKEY_USER_MARKER = "YUBIKEY_USER_MARKER";

  // For disabled OTP auth mode
  private final String YUBIKEY_OTP_PADDING
          = "EaS5ksRVErn2jiOmSQy5LM2X7LgWAZWfWYKQoPavbrhN";

  // For padding when password field is empty
  private final String MOBILE_OTP_PADDING = "123456";

  @EJB
  private UserManager mgr;

  @EJB
  private EmailBean emailBean;

  private String username;
  private String password;
  private String otpCode;
  private User user;
  private int userid;

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public String getOtpCode() {
    return otpCode;
  }

  public void setOtpCode(String otpCode) {
    this.otpCode = otpCode;
  }

  /**
   * Authenticate the users using two factor mobile authentication.
   *
   * @return
   */
  public String login() {

    FacesContext ctx = FacesContext.getCurrentInstance();
    HttpServletRequest req = (HttpServletRequest) ctx.getExternalContext().
            getRequest();

    /*
     * Log out from the existing logged in user
     */
    if (req.getRemoteUser() != null) {
      return logout();
    }

    user = mgr.getUserByEmail(username);
    System.err.println("STEP 1");
    // Add padding if custom realm is disabled
    if (this.otpCode == null || this.otpCode.isEmpty()) {
      this.otpCode = MOBILE_OTP_PADDING;
    }
    System.err.println("STEP 2");
    // Return if username is wrong
    if (user == null) {
      MessagesController.addMessageToGrowl(
              AccountStatusErrorMessages.USER_NOT_FOUND);
      return ("");
    }
    System.err.println("STEP 3");
    // Retrun if user is not Mobile user     
    if (user.getYubikeyUser() == PeopleAccountStatus.YUBIKEY_USER.getValue()) {
      MessagesController.addMessageToGrowl(
              AccountStatusErrorMessages.USER_NOT_FOUND);
      return ("");

    }
    System.err.println("STEP 4");
    // Return if user not activated
    if (user.getStatus() == PeopleAccountStatus.MOBILE_ACCOUNT_INACTIVE.
            getValue()) {
      MessagesController.addMessageToGrowl(
              AccountStatusErrorMessages.INACTIVE_ACCOUNT);
      return ("");
    }
    System.err.println("STEP 5");
    // Return if used is bloked
    if (user.getStatus() == PeopleAccountStatus.ACCOUNT_BLOCKED.getValue()) {
      // Inform the use about the blocked account
      MessagesController.addMessageToGrowl(
              AccountStatusErrorMessages.BLOCKED_ACCOUNT);
      return ("");
    }
    System.err.println("STEP 6");
    // Return if used is bloked
    if (user.getStatus() == PeopleAccountStatus.ACCOUNT_DEACTIVATED.getValue()) {
      // Inform the use about the blocked account
      MessagesController.addMessageToGrowl(
              AccountStatusErrorMessages.DEACTIVATED_ACCOUNT);
      return ("");
    }

    userid = user.getUid();

    registerLoginInfo(user, "AUTHENTICATION");
    System.err.println("STEP 7");
    try {
      // concatenate the static password with the otp due to limitations of passing two passwords to glassfish
      //req.login(this.username, this.password + this.otpCode);
      req.login(this.username, this.password);
      System.err.println("STEP 7.1");
      // Reset the lock for failed accounts
      mgr.resetLock(userid);
      // Set the onlne flag
      mgr.setOnline(userid, 1);

    } catch (ServletException ex) {
      // if more than five times block the account
      int val = user.getFalseLogin();
      mgr.increaseLockNum(userid, val + 1);
      if (val > 5) {
        mgr.changeAccountStatus(userid, "", PeopleAccountStatus.ACCOUNT_BLOCKED.
                getValue());
        try {
          emailBean.sendEmail(user.getEmail(),
                  UserAccountsEmailMessages.ACCOUNT_BLOCKED__SUBJECT,
                  UserAccountsEmailMessages.accountBlockedMessage());
        } catch (MessagingException ex1) {
          Logger.getLogger(CustomAuthentication.class.getName()).log(
                  Level.SEVERE, null, ex1);
        }

      }
      System.err.println("STEP 8");
      // Inform the use about invalid credentials
      MessagesController.addMessageToGrowl(
              AccountStatusErrorMessages.INCCORCT_CREDENTIALS);
      return ("");
    }

    // Reset the password after first login
    if (user.getStatus() == PeopleAccountStatus.ACCOUNT_PENDING.getValue()) {
      return ("reset");
    }
    System.err.println("STEP 9");
    // Go to welcome page
    return ("indexPage");
  }

  public boolean isOTPEnabled() {
    return true;
  }

  public String yubikeyLogin() {

    FacesContext ctx = FacesContext.getCurrentInstance();
    HttpServletRequest req = (HttpServletRequest) ctx.getExternalContext().
            getRequest();

    /*
     * Log out from the existing logged in user
     */
    if (req.getRemoteUser() != null) {
      return logout();

    }

    user = mgr.getUserByEmail(username);

    // Return if username is wrong
    if (user == null) {
      MessagesController.addMessageToGrowl(
              AccountStatusErrorMessages.USER_NOT_FOUND);
      return ("");
    }

    // Retrun if user is not Yubikey user     
    if (user.getYubikeyUser() != PeopleAccountStatus.YUBIKEY_USER.getValue()) {
      MessagesController.addMessageToGrowl(
              AccountStatusErrorMessages.USER_NOT_FOUND);
      return ("");
    }

    // Return if user not activated
    if (user.getStatus() == PeopleAccountStatus.YUBIKEY_ACCOUNT_INACTIVE.
            getValue()) {
      MessagesController.addMessageToGrowl(
              AccountStatusErrorMessages.INACTIVE_ACCOUNT);
      return ("");
    }

    // Return if used is bloked
    if (user.getStatus() == PeopleAccountStatus.ACCOUNT_BLOCKED.getValue()) {
      // Inform the use about the blocked account
      MessagesController.addMessageToGrowl(
              AccountStatusErrorMessages.BLOCKED_ACCOUNT);
      return ("");
    }

    // Return if used is bloked
    if (user.getStatus() == PeopleAccountStatus.ACCOUNT_DEACTIVATED.getValue()) {
      // Inform the use about the blocked account
      MessagesController.addMessageToGrowl(
              AccountStatusErrorMessages.DEACTIVATED_ACCOUNT);
      return ("");
    }

    userid = user.getUid();

    // Add padding if custim realm is disabled
    if (this.otpCode == null || this.otpCode.isEmpty()) {
      this.otpCode = YUBIKEY_OTP_PADDING;
    }

    registerLoginInfo(user, "AUTHENTICATION");

    try {
      // Concatenate the static password with the otp due to limitations of passing two passwords to glassfish
      req.login(this.username, this.password + this.otpCode
              + this.YUBIKEY_USER_MARKER);
      // Reset the lock for failed accounts
      mgr.resetLock(userid);
      // Set the onlne flag
      mgr.setOnline(userid, 1);

    } catch (ServletException ex) {
      // If more than five times block the account
      int val = user.getFalseLogin();
      mgr.increaseLockNum(userid, val + 1);
      if (val > 5) {
        mgr.changeAccountStatus(userid, "", PeopleAccountStatus.ACCOUNT_BLOCKED.
                getValue());
        try {
          emailBean.sendEmail(user.getEmail(),
                  UserAccountsEmailMessages.ACCOUNT_BLOCKED__SUBJECT,
                  UserAccountsEmailMessages.accountBlockedMessage());
        } catch (MessagingException ex1) {
          Logger.getLogger(CustomAuthentication.class.getName()).log(
                  Level.SEVERE, null, ex1);
          return ("");
        }
      }

      // Inform the use about invalid credentials
      MessagesController.addMessageToGrowl(
              AccountStatusErrorMessages.INCCORCT_CREDENTIALS);
      return ("");
    }

    // Reset the password after first login
    if (user.getStatus() == PeopleAccountStatus.ACCOUNT_PENDING.getValue()) {
      return ("reset");
    }

    // Go to welcome page
    return ("indexPage");
  }

  public String logout() {

    FacesContext ctx = FacesContext.getCurrentInstance();
    HttpSession sess = (HttpSession) ctx.getExternalContext().getSession(false);

    if (null != sess) {
      sess.invalidate();
    }
    mgr.setOnline(userid, -1);

    return ("welcome");
  }

  public void registerLoginInfo(User p, String action) {

    HttpServletRequest httpServletRequest = (HttpServletRequest) FacesContext.
            getCurrentInstance().getExternalContext().getRequest();
    String ip = httpServletRequest.getRemoteAddr();

    ExternalContext externalContext = FacesContext.getCurrentInstance().
            getExternalContext();
    String userAgent = externalContext.getRequestHeaderMap().get("User-Agent");

    String browser = null;
    if (userAgent.contains("MSIE")) {
      browser = "Internet Explorer";
    }
    if (userAgent.contains("Firefox")) {
      browser = "Firefox";
    }
    if (userAgent.contains("Chrome")) {
      browser = "Chrome";
    }
    if (userAgent.contains("Opera")) {
      browser = "Opera";
    }
    if (userAgent.contains("Safari")) {
      browser = "Safari";
    }

    mgr.registerLoginInfo(p, action, ip, browser);

  }
}
