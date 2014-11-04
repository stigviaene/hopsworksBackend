package se.kth.bbc.lims;

import javax.faces.application.FacesMessage;
import javax.faces.context.FacesContext;

/**
 *
 * @author stig
 */
public class MessagesController {
    public static final String ERROR = "Error.";
    public static final String SUCCESS = "Success.";  
    
    
    //TODO: replace all occurrences of similar methods elsewhere.
    
    public static void addInfoMessage(String message) {
        addInfoMessage(message,message,null);
    }

    public static void addInfoMessage(String summary, String mess) {
        addInfoMessage(summary, mess, null);
    }
    
    public static void addInfoMessage(String summary, String mess, String anchor) {
        FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_INFO, summary, mess);
        FacesContext.getCurrentInstance().addMessage(anchor, message);
    }

    public static void addErrorMessage(String message) {
        addErrorMessage(message, message, null);
    }
    
    public static void addErrorMessage(String summary, String message){
        addErrorMessage(summary, message, null);
    }

    public static void addErrorMessage(String summary, String message, String anchor) {
        FacesMessage errorMessage = new FacesMessage(FacesMessage.SEVERITY_ERROR, summary, message);
        FacesContext.getCurrentInstance().addMessage(anchor, errorMessage);
    }
}