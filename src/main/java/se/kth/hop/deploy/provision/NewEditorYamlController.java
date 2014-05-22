/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package se.kth.hop.deploy.provision;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import javax.faces.bean.ManagedBean;
import javax.faces.bean.ViewScoped;
import org.primefaces.extensions.event.CompleteEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Controller that handles edition of YAML cluster files using a code console
 * interface.
 *
 * We should be doing the following:
 * http://leonotepad.blogspot.se/2014/01/playing-with-antlr4-primefaces.html
 *
 * @author Jim
 */
@ManagedBean
@ViewScoped
public class NewEditorYamlController implements Serializable {

    private static final long serialVersionUID = 20131126L;
    Logger logger = LoggerFactory.getLogger(NewEditorYamlController.class);

    private String content;
    private String mode = "yaml";

    public NewEditorYamlController() {
    }

    public void init() {
//        this.content = getOneNodeCluster();
    }

    public String getContent() {
        return content;
    }

    public void setContent(final String content) {
        this.content = content;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(final String mode) {
        this.mode = mode;
    }

    public void invokeJclouds() {
//        return "editcluster";
    }

    public String getOneNodeCluster() {
        StringBuilder sb = new StringBuilder();

        sb.append("---\n"
                + "name: E2ETestCluster\n"
                + "provider: {name: vagrant}\n"
                + "git: {url: \"https://github.com/hopstart/ <https://github.com/hopstart/>\", user: jdowling}\n"
                + "\n"
                + "group: \n"
                + "  name: g1\n"
                + "  size: 1 \n"
                + "  recipes: [hopagent, ndb/ndbd, ndb/mysqld, ndb/mgmd, hopdashboard, hop/nn, hop/dn, hop/rm, hop/nm, hop/jhs]\n"
                + "  attr: ndb/ndbapi/private_ips=$private_ips\n"
                + "  attr: ndb/ndbapi/public_ips=$public_ips\n"
        );
        setContent(sb.toString());
        return sb.toString();
    }

    public String getThreeNodeCluster() {
        StringBuilder sb = new StringBuilder();
        sb.append("---\n"
                + "name: E2ETestCluster\n"
                + "provider: {name: vagrant}\n"
                + "git: {url: \"https://github.com/hopstart/ <https://github.com/hopstart/>\",\n"
                + "user: jdowling}\n"
                + "attr: ndb/ndbapi/private_ips=$g1.private_ips\n"
                + "attr: ndb/ndbapi/public_ips=$g1.public_ips\n"
                + "\n"
                + "group: \n"
                + "  name: g1\n"
                + "  size: 1 \n"
                + "  recipes: [hopagent, hopdashboard, ndb/mysqld, ndb/mgmd, hop/nn, hop/rm,\n"
                + "hop/nm, hop/jhs]\n"
                + "\n"
                + "group: \n"
                + "  name: g2\n"
                + "  size: 2 \n"
                + "  recipes: [hopagent, ndb/ndbd, hop/dn]"
                + ""
                + ""
        );

        setContent(sb.toString());
        return sb.toString();

    }

    public String getFiveNodeCluster() {
        StringBuilder sb = new StringBuilder();
        sb.append("!!se.kth.hop.deploy.virtualization.parser.Cluster \n "
                + "name: test2 \n  installPhase: true\n   global:\n     recipes:\n   - ssh\n - chefClient "
                + "authorizePorts:  \n      - 3306 \n     - 4343  \n     - 3321 \n    git: \n"
                + "      user: Jim Dowling \n      repository: https://github.com/hopstart/hop-chef.git \n"
                + "      key: notNull \n  provider: \n    name: aws-ec2 \n    instanceType: m1.large \n"
                + "    loginUser: ubuntu \n    image: eu-west-1/ami-ffcdce8b \n    region: eu-west-1 \n"
                + "  NodeGroup: \n  - service: ndb \n    number: 2 \n  - service: mgm \n    number: 1 \n"
                + "  - service: mysqld \n    number: 1 \n    recipes:  \n      - namenode \n  - service: namenode \n"
                + "    number: 1 \n    recipes: \n      - resourcemanager \n  - service: datanode \n"
                + "    number: 2 \n    recipes: \n      - nodemanager \n... \n"
        );

        setContent(sb.toString());
        return sb.toString();

    }

    public List<String> complete(final CompleteEvent event) {
        try {
            return completeEjb(event.getToken());
        } catch (Exception ex) {
            java.util.logging.Logger.getLogger(NewEditorYamlController.class.getName()).log(Level.SEVERE, null, ex);
        }
        return null;
    }

    private static final String[] GIT = {"user", "url"};
    private static final String[] GIT_URL = {"https://github.com/", "https://github.com/hopstart/"};

    public List<String> completeEjb(String token) throws Exception {
        if (token == null || token.trim().length() == 0) {
            return null;
        } else {
            List<String> suggestions = new ArrayList<String>();

            switch (token) {
                case "git":
                    for (String v : GIT) {
                        suggestions.add(v);
                    }
                case "url":
                    for (String v : GIT_URL) {
                        suggestions.add(v);
                    }
            }
            return suggestions;
        }

    }

}