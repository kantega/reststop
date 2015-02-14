package org.kantega.reststop.deploy.model;

/**
 *
 */
public class Plugin {
    private String groupId;
    private String artifactId;
    private String version;

    public Plugin(String groupId, String artifactId, String version) {

        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
    }

    public Plugin(String gav) {
        String[] comps  = gav.split(":");
        this.groupId = comps[0];
        this.artifactId = comps[1];
        this.version = comps[2];
    }

    public Plugin() {
    }

    public String getGroupId() {
        return groupId;
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getCoords() {
        return getGroupId() +":" + getArtifactId() +":" + getVersion();
    }
}
