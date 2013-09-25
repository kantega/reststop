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

import org.kantega.reststop.api.DefaultReststopPlugin;
import org.kantega.reststop.api.FilterPhase;
import org.kantega.reststop.api.PluginListener;
import org.kantega.reststop.api.Reststop;

import javax.servlet.ServletContext;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class DevelopmentPlugin extends DefaultReststopPlugin {
    private boolean pluginManagerStarted;

    public DevelopmentPlugin(final Reststop reststop, ServletContext servletContext) {

        Map<String, Map<String, Object>> pluginsClasspathMap = (Map<String, Map<String, Object>>) servletContext.getAttribute("pluginsClasspathMap");


        if(! loadedByDevelopmentClassLoader()) {
            for (String pluginId : pluginsClasspathMap.keySet()) {
                if(pluginId.startsWith("org.kantega.reststop:reststop-development-plugin")) {
                    File sourceDirectory  = (File) pluginsClasspathMap.get(pluginId).get("sourceDirectory");
                    if(sourceDirectory != null) {
                        Map<String, Object> info = pluginsClasspathMap.get(pluginId);
                        ClassLoader pluginParentClassLoader = reststop.getPluginParentClassLoader();
                        final DevelopmentClassloader devloader = createClassLoader(sourceDirectory, info, pluginParentClassLoader);

                        addPluginListener(new PluginListener() {
                            @Override
                            public void pluginManagerStarted() {
                                pluginManagerStarted = true;
                                reststop.changePluginClassLoaders().remove(getClass().getClassLoader()).add(devloader).commit();
                            }
                        });

                        return;
                    }

                }
            }
        }

        final DevelopmentClassLoaderProvider provider = new DevelopmentClassLoaderProvider();


        for (String pluginId : pluginsClasspathMap.keySet()) {
            if(pluginId.startsWith("org.kantega.reststop:reststop-development-plugin:")) {
                File sourceDirectory  = (File) pluginsClasspathMap.get(pluginId).get("sourceDirectory");

                provider.addExistingClassLoader(pluginId, createClassLoader(sourceDirectory, pluginsClasspathMap.get(pluginId), reststop.getPluginParentClassLoader()));

            }
            File sourceDirectory  = (File) pluginsClasspathMap.get(pluginId).get("sourceDirectory");
            if(sourceDirectory != null) {
                provider.addPluginInfo(pluginId, pluginsClasspathMap.get(pluginId));
            }

        }

        

        if(!loadedByDevelopmentClassLoader()) {
            addPluginListener(new PluginListener() {
                @Override
                public void pluginManagerStarted() {
                    provider.start(reststop);
                }
            });
        } else {
            provider.start(reststop);
        }

        addServletFilter(reststop.createFilter(new RedeployFilter(provider, reststop), "/*", FilterPhase.UNMARSHAL));

    }

    private DevelopmentClassloader createClassLoader(File sourceDirectory, Map<String, Object> info, ClassLoader pluginParentClassLoader) {
        List<File> runtime  = (List<File>) info.get("runtime");
        List<File> compile  = (List<File>) info.get("compile");
        List<File> test  = (List<File>) info.get("test");

        return new DevelopmentClassloader(sourceDirectory, compile, runtime, test, pluginParentClassLoader);
    }

    private boolean loadedByDevelopmentClassLoader() {
        return getClass().getClassLoader().getClass().getName().equals(DevelopmentClassloader.class.getName());
    }

    private List<File> parseClasspath(String classPath) {
        List<File> files = new ArrayList<>();
        for(String path : classPath.split(File.pathSeparator)) {
            File file = new File(path);
            files.add(file);
        }
        return files;
    }
}
