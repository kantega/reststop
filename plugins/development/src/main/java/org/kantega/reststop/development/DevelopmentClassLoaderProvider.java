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
import org.kantega.reststop.api.ReststopPlugin;
import org.kantega.reststop.classloaderutils.*;

import java.io.File;
import java.util.*;

/**
 *
 */
public class DevelopmentClassLoaderProvider {

    private final Map<String, PluginInfo> pluginsInfo = new LinkedHashMap<>();
    private Map<String, DevelopmentClassloader> classloaders = new HashMap<>();
    private Map<String, PluginClassLoader> byDepsId = new HashMap<>();

    private Reststop reststop;



    public void addPluginInfo(PluginInfo info) {
        pluginsInfo.put(info.getPluginId(), info);
    }

    public void addExistingClassLoader(String pluginId, DevelopmentClassloader loader) {
        classloaders.put(pluginId, loader);
    }
    public synchronized void start(Reststop reststop) {
        ClassLoader parentClassLoader = reststop.getPluginParentClassLoader();
        this.reststop = reststop;

        Reststop.PluginClassLoaderChange change = reststop.changePluginClassLoaders();


        for (PluginInfo pluginInfo : this.pluginsInfo.values()) {

            if( !pluginInfo.isDirectDeploy()) {
                List<File> runtime = pluginInfo.getClassPathFiles("runtime");
                List<File> compile = pluginInfo.getClassPathFiles("compile");
                List<File> test = pluginInfo.getClassPathFiles("test");
                File sourceDir = pluginInfo.getSourceDirectory();

                DevelopmentClassloader classloader = new DevelopmentClassloader(pluginInfo, sourceDir,
                        compile,
                        runtime,
                        test,
                        getParentClassLoader(pluginInfo, parentClassLoader));

                classloaders.put(pluginInfo.getPluginId(), classloader);
                byDepsId.put(pluginInfo.getGroupIdAndArtifactId(), classloader);
                change.add(classloader);
            }
        }
        change.commit();

    }
    private ClassLoader getParentClassLoader(PluginInfo pluginInfo, ClassLoader parentClassLoader) {
        Set<ClassLoader> delegates = new HashSet<>();

        for (Artifact dep : pluginInfo.getClassPath("compile")) {
            ClassLoader dependencyLoader = byDepsId.get(dep.getGroupIdAndArtifactId());
            if (dependencyLoader != null) {
                delegates.add(dependencyLoader);
            }
        }
        if (delegates.isEmpty()) {
            return parentClassLoader;
        } else {
            return new ResourceHidingClassLoader(new DelegateClassLoader(parentClassLoader, delegates), ReststopPlugin.class);
        }
    }


    public Map<String, DevelopmentClassloader> getClassloaders() {
        return new HashMap<>(classloaders);
    }

    public synchronized DevelopmentClassloader redeploy(String pluginId, DevelopmentClassloader classloader) {

        classloader.compileSources();
        classloader.copySourceResorces();
        PluginInfo info = classloader.getPluginInfo();
        DevelopmentClassloader newClassLoader = new DevelopmentClassloader(classloader, getParentClassLoader(info, getParentClassLoader(info, reststop.getPluginParentClassLoader())));

        byDepsId.put(info.getGroupIdAndArtifactId(), newClassLoader);

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
            Map<String, PluginInfo> deps = new HashMap<>();
            getChildPlugins(info, deps, new ArrayList<>(this.pluginsInfo.values()));

            List<PluginInfo> sorted = PluginInfo.sortByRuntimeDependencies(new ArrayList<>(deps.values()));
            List<PluginInfo> reverse = new ArrayList<>(sorted);
            Collections.reverse(reverse);

            for (PluginInfo pluginInfo : reverse) {
                change.remove(classloaders.get(pluginInfo.getPluginId()));
            }
            for (PluginInfo pluginInfo : sorted) {
                ClassLoader parent = getParentClassLoader(pluginInfo, reststop.getPluginParentClassLoader());
                DevelopmentClassloader newDepLoader = new DevelopmentClassloader(classloaders.get(pluginInfo.getPluginId()), parent);
                change.add(newDepLoader);
                classloaders.put(pluginInfo.getPluginId(), newDepLoader);
                byDepsId.put(pluginInfo.getGroupIdAndArtifactId(), newDepLoader);

            }
            change.remove(classloader);
            change.add(newClassLoader);
            classloaders.put(pluginId, newClassLoader);

        }
        change.commit();
        return newClassLoader;

    }

    private void getChildPlugins(PluginInfo info, Map<String, PluginInfo> children, List<PluginInfo> all) {

        for (PluginInfo child : info.getChildren(all)) {
            if(!children.containsKey(child.getPluginId())) {
                children.put(child.getPluginId(), child);
                getChildPlugins(child, children, all);
            }
        }
    }

    public void addByDepartmentId(PluginInfo info, PluginClassLoader classLoader) {
        byDepsId.put(info.getGroupIdAndArtifactId(), classLoader);
    }
}
