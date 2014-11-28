package se.kth.bbc.study;

import java.io.Serializable;
import javax.ejb.EJB;
import javax.ejb.EJBException;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ManagedProperty;
import javax.faces.bean.SessionScoped;
import javax.faces.bean.ViewScoped;
import org.primefaces.context.RequestContext;
import se.kth.bbc.activity.ActivityController;
import se.kth.bbc.activity.ActivityMB;
import se.kth.bbc.lims.MessagesController;

/**
 * Class for displaying the "confirm remove member" dialog. Implemented out
 * of pure necessity because passing variables from a ui:repeat inside a p:dialog
 * simply does not seem to work.
 * 
 * @author stig
 */
@ManagedBean
@SessionScoped
public class RemoveMemberDlgController implements Serializable{
    
    private String name;
    private String email;
    
    @EJB
    private transient StudyTeamController studyTeamController;
    
    @ManagedProperty(value = "#{activityBean}")
    private transient ActivityMB activity;    
    
    @ManagedProperty(value = "#{studyManagedBean}")
    private transient StudyMB studyMB;

    public void setActivity(ActivityMB activity) {
        this.activity = activity;
    }
    
    public void setStudyMB(StudyMB studyMB) {
        this.studyMB = studyMB;
    }
    
    public void showDialog(String name, String email){
        this.name=name;
        this.email=email;
        RequestContext.getCurrentInstance().openDialog("dialogs/removeTeamMemberConfirm");
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
    
    public void closeDlg(){
        RequestContext.getCurrentInstance().closeDialog(null);
    }
    
    public void removeMem(){        
        deleteMemberFromTeam(email);
        closeDlg();
    }
    
    public synchronized String deleteMemberFromTeam(String email) {
        try {

            studyTeamController.removeStudyTeam(studyMB.getStudyName(), email);
            activity.addActivity(ActivityController.REMOVED_MEMBER + email, studyMB.getStudyName(), "TEAM");

        } catch (EJBException ejb) {
            MessagesController.addErrorMessage("Deleting team member failed.");
            return null;
        }
            MessagesController.addInfoMessage("Member removed","Team member " + email + " deleted from study " + studyMB.getStudyName());            
            return "studyPage";

    }
    
    
}