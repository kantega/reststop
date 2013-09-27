package org.kantega.reststop.core;

import java.io.File;

/**
 *
 */
public class Artifact {
    private final String groupId;
    private final String artifactId;
    private final String version;
    private final File file;

    public Artifact(String groupId, String artifactId, String version, File file) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
        this.file = file;
    }

    public Artifact(PluginInfo info) {
        this(info.getGroupId(), info.getArtifactId(), info.getVersion(), null);
    }

    public String getGroupId() {
        return groupId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public String getVersion() {
        return version;
    }

    public File getFile() {
        return file;
    }

    public String getPluginId() {
        return getGroupId() + ":" + getArtifactId() + ":" + getVersion();
    }

    public String getGroupIdAndArtifactId() {
        return getGroupId() +":" + getArtifactId();
    }
}
