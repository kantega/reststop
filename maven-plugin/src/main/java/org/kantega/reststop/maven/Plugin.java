/*
 * Copyright 2013 Kantega AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kantega.reststop.maven;

import org.eclipse.aether.artifact.Artifact;
import org.kantega.reststop.classloaderutils.PluginInfo;

import java.io.File;
import java.util.Properties;

/**
 *
 */
public class Plugin {
    private String groupId;
    private String artifactId;
    private String version;
    private boolean directDeploy = true;
    private File sourceDirectory;
    private Properties config;

    public Plugin(String groupId, String artifactId, String version) {
        this.groupId = groupId;
        this.artifactId = artifactId;
        this.version = version;
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


    public boolean isDirectDeploy() {
        return directDeploy;
    }

    public void setDirectDeploy(boolean directDeploy) {
        this.directDeploy = directDeploy;
    }

    public File getSourceDirectory() {
        return sourceDirectory;
    }

    public void setSourceDirectory(File sourceDirectory) {
        this.sourceDirectory = sourceDirectory;
    }

    public PluginInfo asPluginInfo() {
        PluginInfo info = new PluginInfo();


        info.setGroupId(getGroupId());
        info.setArtifactId(getArtifactId());
        info.setVersion(getVersion());


        info.setDirectDeploy(isDirectDeploy());

        info.setSourceDirectory(getSourceDirectory());

        info.setConfig(config);



        return info;

    }
}
