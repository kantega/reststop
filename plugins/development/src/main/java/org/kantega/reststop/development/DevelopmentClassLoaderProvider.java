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

package org.kantega.reststop.development;

import org.kantega.reststop.api.Reststop;

import java.io.File;
import java.util.*;

/**
 *
 */
public class DevelopmentClassLoaderProvider {

    private final Map<String, Map<String, Object>> pluginsInfo = new HashMap<>();
    private Map<String, DevelopmentClassloader> classloaders = new HashMap<>();

    private Reststop reststop;



    public void addPluginInfo(String pluginId, Map<String, Object> info) {
        pluginsInfo.put(pluginId, info);
    }

    public void addExistingClassLoader(String pluginId, DevelopmentClassloader loader) {
        classloaders.put(pluginId, loader);
    }
    public synchronized void start(Reststop reststop) {
        ClassLoader parentClassLoader = reststop.getPluginParentClassLoader();
        this.reststop = reststop;

        Reststop.PluginClassLoaderChange change = reststop.changePluginClassLoaders();


        for (String pluginId : pluginsInfo.keySet()) {
            Map<String, Object> pluginInfo = pluginsInfo.get(pluginId);
            if( Boolean.FALSE.equals(pluginInfo.get("directDeploy"))) {
                List<File> runtime = (List<File>) pluginInfo.get("runtime");
                List<File> compile = (List<File>) pluginInfo.get("compile");
                List<File> test = (List<File>) pluginInfo.get("test");
                File sourceDir = (File) pluginInfo.get("sourceDirectory");

                DevelopmentClassloader classloader = new DevelopmentClassloader(sourceDir, compile, runtime, test, parentClassLoader);

                classloaders.put(pluginId, classloader);
                change.add(classloader);
            }
        }
        change.commit();

    }


    public Map<String, DevelopmentClassloader> getClassloaders() {
        return new HashMap<>(classloaders);
    }

    public synchronized DevelopmentClassloader redeploy(String pluginId, DevelopmentClassloader classloader) {

        classloader.compileSources();
        classloader.copySourceResorces();
        DevelopmentClassloader newClassLoader = new DevelopmentClassloader(classloader);

        Reststop.PluginClassLoaderChange change = reststop.changePluginClassLoaders();
        if(pluginId.startsWith("org.kantega.reststop:reststop-development-plugin:")) {
            for (DevelopmentClassloader developmentClassloader : classloaders.values()) {
                if(developmentClassloader != classloader) {
                    change.remove(developmentClassloader);
                }

            }
            change.remove(getClass().getClassLoader());
            change.add(newClassLoader);
        } else {
            change
                    .remove(classloader)
                    .add(newClassLoader);

        }
        change.commit();
        classloaders.put(pluginId, newClassLoader);
        return newClassLoader;

    }

}
