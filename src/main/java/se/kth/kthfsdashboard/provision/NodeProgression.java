/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package se.kth.kthfsdashboard.provision;

import java.io.Serializable;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.NamedQueries;
import javax.persistence.NamedQuery;
import javax.persistence.Table;

/**
 *
 * @author Alberto Lorente Leal <albll@kth.se>
 */
@Entity
@Table(name = "NodeProgress")
@NamedQueries({
    @NamedQuery(name = "NodeProgression.findAll", query = "SELECT c FROM NodeProgression c"),
    @NamedQuery(name = "NodeProgression.findNodeByNodeID", query =
    "SELECT c FROM NodeProgression c WHERE c.nodeId = :nodeId"),
        @NamedQuery(name = "NodeProgression.AllNodeByClusterName",  
        query = "SELECT c FROM NodeProgression c where c.cluster = :clusterName"),
        @NamedQuery(name = "NodeProgression.clearComplete", query = 
        "SELECT c FROM NodeProgression c WHERE c.phase = :phase")
})
public class NodeProgression implements Serializable {

    private static final long serialVersionUID = 1L;
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String cluster;
    private String nodeId;
    private String nodeRole;
    private String phase;
    private String previousPhase;
    private String loginUser;
    private String ip;
    @Column(columnDefinition = "text")
    private String privateKey;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCluster() {
        return cluster;
    }

    public void setCluster(String cluster) {
        this.cluster = cluster;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getRole() {
        return nodeRole;
    }

    public void setRole(String role) {
        this.nodeRole = role;
    }

    public String getPhase() {
        return phase;
    }

    public String getPreviousPhase() {
        return previousPhase;
    }

    public void setPreviousPhase(String previousPhase) {
        this.previousPhase = previousPhase;
    }

    public void setPhase(String phase) {
        this.phase = phase;
    }

    public String getLoginUser() {
        return loginUser;
    }

    public void setLoginUser(String loginUser) {
        this.loginUser = loginUser;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getPrivateKey() {
        return privateKey;
    }

    public void setPrivateKey(String publicKey) {
        this.privateKey = publicKey;
    }

    
    @Override
    public int hashCode() {
        int hash = 17;
        hash += (id != null ? id.hashCode() : 0);
        hash += (cluster != null ? cluster.hashCode() : 0);
        hash += (nodeRole != null ? nodeRole.hashCode() : 0);
        hash += (phase != null ? phase.hashCode() : 0);
        hash += (previousPhase != null ? previousPhase.hashCode() : 0);
        hash += (nodeId != null ? nodeId.hashCode() : 0);
        hash += (loginUser != null ? loginUser.hashCode() : 0);
        hash += (ip != null ? ip.hashCode() : 0);
        hash += (privateKey != null ? privateKey.hashCode() : 0);
        return hash;
    }

    @Override
    public boolean equals(Object object) {
        // TODO: Warning - this method won't work in the case the id fields are not set
        if (!(object instanceof NodeProgression)) {
            return false;
        }
        NodeProgression other = (NodeProgression) object;
        if ((this.id == null && other.id != null) || (this.id != null && !this.id.equals(other.id))) {
            return false;
        }
        if ((this.cluster == null && other.cluster != null)
                || (this.cluster != null && !this.cluster.equals(other.cluster))) {
            return false;
        }
        if ((this.nodeRole == null && other.nodeRole != null)
                || (this.nodeRole != null && !this.nodeRole.equals(other.nodeRole))) {
            return false;
        }
        if ((this.phase == null && other.phase != null)
                || (this.phase != null && !this.phase.equals(other.phase))) {
            return false;
        }
        if ((this.previousPhase == null && other.previousPhase != null)
                || (this.previousPhase != null && !this.previousPhase.equals(other.previousPhase))) {
            return false;
        }
         if ((this.nodeId == null && other.nodeId != null) || 
                 (this.nodeId != null && !this.nodeId.equals(other.nodeId))) {
            return false;
        }
         if ((this.loginUser == null && other.loginUser != null) || 
                 (this.loginUser != null && !this.loginUser.equals(other.loginUser))) {
            return false;
        }
         if ((this.privateKey == null && other.privateKey != null) || 
                 (this.privateKey != null && !this.privateKey.equals(other.privateKey))) {
            return false;
        }
         if ((this.ip == null && other.ip != null) || 
                 (this.ip != null && !this.ip.equals(other.ip))) {
            return false;
        }
        return true;
    }

    @Override
    public String toString() {
        return "NodeProgression{" + "id=" + id + ", cluster=" + cluster + ", nodeId=" + nodeId 
                + ", nodeRole=" + nodeRole + ", phase=" + phase + ", previousPhase=" + previousPhase 
                + ", loginUser=" + loginUser + ", ip=" + ip + ", privateKey=" + privateKey + '}';
    }

    
}