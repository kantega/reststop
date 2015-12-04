/*
 * Copyright 2015 Kantega AS
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

package org.kantega.reststop.maven.dist;

import java.io.File;

/**
 *
 */
public class Resource {

    File directory;
    String targetDirectory;
    String[] excludes;
    String[] includes;
    String mode;
    private String owner;
    private String group;

    public File getDirectory() {
        return directory;
    }

    public void setDirectory(File directory) {
        this.directory = directory;
    }

    public String getTargetDirectory() {
        return targetDirectory;
    }

    public void setTargetDirectory(String targetDirectory) {
        this.targetDirectory = targetDirectory;
    }

    public String[] getExcludes() {
        return excludes;
    }

    public void setExcludes(String[] excludes) {
        this.excludes = excludes;
    }

    public String[] getIncludes() {
        return includes;
    }

    public void setIncludes(String[] includes) {
        this.includes = includes;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public String getOwner() {
        return owner;
    }

    public void setOwner(String owner) {
        this.owner = owner;
    }


    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }
}
