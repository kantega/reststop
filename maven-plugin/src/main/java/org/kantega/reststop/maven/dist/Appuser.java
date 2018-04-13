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
public class Appuser {

    public static Appuser DEFAULT = new Appuser();

    private String username = "%{name}";
    private String groupname= "%{name}";
    private String homeDir = "/opt/%{name}/jetty";

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getGroupname() {
        return groupname;
    }

    public void setGroupname(String groupname) {
        this.groupname = groupname;
    }

    public String getHomeDir() {
        return homeDir;
    }

    public void setHomeDir(String homeDir) {
        this.homeDir = homeDir;
    }

    public static Appuser applyDefaults(Appuser user){
        if( user == null)
            return new Appuser();

        if( user.getGroupname() == null)
            user.setGroupname(DEFAULT.getGroupname());

        if( user.getUsername() == null)
            user.setUsername(DEFAULT.getUsername());

        if( user.getHomeDir() == null)
            user.setHomeDir(DEFAULT.getHomeDir());

        return user;
    }
}
