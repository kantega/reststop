/*
 * Copyright 2018 Kantega AS
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

/**

 */
public class FilePerm {

    public static final FilePerm DEFAULT = new FilePerm();
    private String fileMode = "0660";
    private String dirMode = "0770";
    private String execMode = "0750";
    private String user = "%{name}";
    private String group = "%{name}";

    public FilePerm() {
    }

    public String getFileMode() {
        return fileMode;
    }

    public void setFileMode(String fileMode) {
        this.fileMode = standardizeMode(fileMode);
    }

    public String getDirMode() {
        return dirMode;
    }

    public void setDirMode(String dirMode) {
        this.dirMode = standardizeMode(dirMode);
    }

    public String getUser() {
        return user;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public String getExecMode() {
        return execMode;
    }

    public void setExecMode(String execMode) {
        this.execMode = standardizeMode(execMode);
    }

    private static String standardizeMode(String mode){
        if( mode == null)
            return null;

        if( mode.length() == 3)
            return "0"+mode;

        return mode;
    }
}

