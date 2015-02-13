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

package org.kantega.reststop.webjars;

import org.kantega.reststop.api.*;

import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;

/**
 * Adds WebJarsFilter with webjars resources, and exports a map of all <artifactId>:<version>, for resource in filter,
 * making it easy to reference webjars in html files.
 */
public class WebjarsReststopPlugin extends DefaultReststopPlugin {

    @Export
    private final WebjarsVersions v;

    private Map<String, String> versions;

    public WebjarsReststopPlugin(final Reststop reststop, final ReststopPluginManager reststopPluginManager) {

        addServletFilter(reststop.createFilter(new WebJarsFilter(reststopPluginManager), "/webjars/*", FilterPhase.USER));

        v = new WebjarsVersions() {
            @Override
            public Map<String, String> getVersions() {
                return getVersionsForWebJars(reststopPluginManager);
            }
        };
    }

    private synchronized Map<String, String> getVersionsForWebJars(ReststopPluginManager reststopPluginManager) {

        if (versions == null) {
            versions = new HashMap<>();

            Set<String> webjars = new HashSet<>();

            try {
                for (ClassLoader loader : reststopPluginManager.getPluginClassLoaders()) {
                    Enumeration<URL> resources = loader.getResources("META-INF/resources/webjars/");
                    while (resources.hasMoreElements()) {
                        URL webJar = resources.nextElement();
                        String file = URLDecoder.decode(webJar.getFile(), "UTF-8");
                        file = file.substring(0, file.indexOf("!"));
                        webjars.add(file);

                    }
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            for (String webjar : webjars) {
                String file = webjar.substring(0, webjar.lastIndexOf("/"));

                String version = file.substring(file.lastIndexOf("/") + 1);
                String artifact = file.substring(0, file.lastIndexOf("/"));
                String artifactId = artifact.substring(artifact.lastIndexOf("/") + 1);

                if (version.contains("-")) {
                    version = version.substring(0, version.lastIndexOf("-"));
                }

                versions.put("versions." + artifactId, version);
            }

            return versions;
        } else {
            return versions;
        }
    }
}