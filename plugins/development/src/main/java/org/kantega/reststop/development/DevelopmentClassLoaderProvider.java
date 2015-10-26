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

package org.kantega.reststop.development;

import org.kantega.reststop.core.Reststop;
import org.kantega.reststop.classloaderutils.*;

import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;
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


        List<PluginInfo> toStart = new ArrayList<>();

        for (PluginInfo pluginInfo : this.pluginsInfo.values()) {

            if( !pluginInfo.isDirectDeploy()) {
                List<File> runtime = getClasspath(pluginInfo, "runtime");
                List<File> compile = getClasspath(pluginInfo, "compile");
                List<File> test = getClasspath(pluginInfo, "test");
                File sourceDir = pluginInfo.getSourceDirectory();

                DevelopmentClassloader classloader = new DevelopmentClassloader(pluginInfo, sourceDir, pluginInfo.getFile(),
                        compile,
                        runtime,
                        test,
                        getParentClassLoader(pluginInfo, parentClassLoader), 1);

                classloaders.put(pluginInfo.getPluginId(), classloader);
                byDepsId.put(pluginInfo.getGroupIdAndArtifactId(), classloader);
                toStart.add(pluginInfo);
            }
        }
        for(DevelopmentClassloader stale : findStaleClassLoaders()) {
            try {
                stale.compileSources();
            } catch (JavaCompilationException e) {
                StringBuilder message = new StringBuilder("Compilation failed in " + stale.getPluginInfo().getArtifactId() +": ");
                for (Diagnostic<? extends JavaFileObject> diagnostic : e.getDiagnostics()) {
                    String sourceFile = diagnostic.getSource().getName();
                    long lineNumber = diagnostic.getLineNumber();
                    long columnNumber = diagnostic.getColumnNumber();
                    String msg = diagnostic.getMessage(Locale.getDefault());

                    message.append(sourceFile).append("[").append(lineNumber).append(":").append(columnNumber).append("]\n").append(msg);
                }
                throw new RuntimeException(message.toString(), e);
            }
            stale.copySourceResorces();
        }

        Reststop.PluginClassLoaderChange change = reststop.changePluginClassLoaders();

        List<PluginInfo> pluginsInStartupOrder = PluginInfo.resolveStartupOrder(toStart);

        for (PluginInfo pluginInfo : pluginsInStartupOrder) {
            change.add(byDepsId.get(pluginInfo.getGroupIdAndArtifactId()));
        }
        change.commit();

    }

    private List<File> getClasspath(PluginInfo pluginInfo, String scope) {
        List<File> files = new ArrayList<>();
        for (Artifact artifact : pluginInfo.getClassPath(scope)) {
            PluginInfo asPlugin = this.pluginsInfo.get(artifact.getPluginId());
            if(asPlugin != null && asPlugin.getSourceDirectory() != null)  {
                files.add(new File(asPlugin.getSourceDirectory(), "target/classes"));
            } else {
                files.add(artifact.getFile());
            }
        }
        return files;
    }

    private ClassLoader getParentClassLoader(PluginInfo pluginInfo, ClassLoader parentClassLoader) {
        Set<PluginClassLoader> delegates = new HashSet<>();

        for (Artifact dep : pluginInfo.getDependsOn()) {
            PluginClassLoader dependencyLoader = byDepsId.get(dep.getGroupIdAndArtifactId());
            if (dependencyLoader != null) {
                delegates.add(dependencyLoader);
            }
        }
        if (delegates.isEmpty()) {
            return parentClassLoader;
        } else {
            return new ResourceHidingClassLoader(new DelegateClassLoader(parentClassLoader, delegates), Object.class) {
                @Override
                protected boolean isLocalResource(String name) {
                    return name.startsWith("META-INF/services/ReststopPlugin/");
                }
            };
        }
    }


    public Map<String, DevelopmentClassloader> getClassloaders() {
        return new HashMap<>(classloaders);
    }

    public synchronized Collection<DevelopmentClassloader> redeploy(List<DevelopmentClassloader> staleClassLoaders) {

        Map<String, DevelopmentClassloader> newClassLoaders = new HashMap<>();
        Map<String, DevelopmentClassloader> oldClassLoaders = new HashMap<>();
        List<PluginInfo> infos = new ArrayList<>();
        for (DevelopmentClassloader classloader : staleClassLoaders) {
            oldClassLoaders.put(classloader.getPluginInfo().getGroupIdAndArtifactId(), classloader);
            DevelopmentClassloader newClassLoader = newClassLoader(classloader);
            newClassLoader.setFailed(true);
            newClassLoaders.put(classloader.getPluginInfo().getGroupIdAndArtifactId(), newClassLoader);
            byDepsId.put(classloader.getPluginInfo().getGroupIdAndArtifactId(), newClassLoader);
            classloaders.put(classloader.getPluginInfo().getPluginId(), newClassLoader);
            infos.add(classloader.getPluginInfo());
        }


        // If the development plugin has changed, we need to restart the world
        // (that is remove all non-dev-plugin classloaders and restart the dev-plugin)
        if(containsDevelopmentPlugin(infos)) {
            Reststop.PluginClassLoaderChange change = reststop.changePluginClassLoaders();

            for (DevelopmentClassloader classloader : classloaders.values()) {
                if(! isDevelopmentClassLoader(classloader.getPluginInfo())) {
                    change.remove(classloader);
                }

            }
            change.remove(getClass().getClassLoader());
            change.add(newClassLoaders.get("org.kantega.reststop:reststop-development-plugin"));
            change.commit();

        } else {
            {
                Reststop.PluginClassLoaderChange change = reststop.changePluginClassLoaders();
                // First remove all classloaders in opposite startup order
                List<PluginInfo> shutdownOrder = PluginInfo.resolveStartupOrder(infos);
                Collections.reverse(shutdownOrder);
                for (PluginInfo pluginInfo : shutdownOrder) {
                    DevelopmentClassloader oldClassLoader = oldClassLoaders.get(pluginInfo.getGroupIdAndArtifactId());
                    if(!oldClassLoader.isFailed()) {
                        change.remove(oldClassLoader);
                    }
                }
                change.commit();
            }

            {

                // Then start in startup order
                List<PluginInfo> startupOrder = PluginInfo.resolveStartupOrder(infos);
                for (PluginInfo pluginInfo : startupOrder) {
                    DevelopmentClassloader newClassLoader = newClassLoaders.get(pluginInfo.getGroupIdAndArtifactId());
                    reststop.changePluginClassLoaders()
                            .add(newClassLoader)
                    .commit();
                    newClassLoader.setFailed(false);
                }
            }


        }
        return newClassLoaders.values();
    }

    private boolean containsDevelopmentPlugin(List<PluginInfo> infos) {
        for (PluginInfo info : infos) {
            if(isDevelopmentClassLoader(info)) {
                return true;
            }
        }
        return false;
    }

    private boolean isDevelopmentClassLoader(PluginInfo info) {
        return info.getPluginId().startsWith("org.kantega.reststop:reststop-development-plugin");
    }

    public DevelopmentClassloader newClassLoader(DevelopmentClassloader classloader) {
        return new DevelopmentClassloader(classloader, getParentClassLoader(classloader.getPluginInfo(), getParentClassLoader(classloader.getPluginInfo(), reststop.getPluginParentClassLoader())));
    }


    public void addByDepartmentId(PluginInfo info, PluginClassLoader classLoader) {
        byDepsId.put(info.getGroupIdAndArtifactId(), classLoader);
    }

    public List<PluginInfo> getPluginInfos() {
        return new ArrayList<>(pluginsInfo.values());
    }

    private void getServiceConsumingPlugins(PluginInfo info, Map<String, PluginInfo> children, List<PluginInfo> all) {


        for (PluginInfo consumer : info.getServiceConsumers(all)) {
            if(!children.containsKey(consumer.getPluginId())) {
                children.put(consumer.getPluginId(), consumer);
                getServiceConsumingPlugins(consumer, children, all);
            }
        }
    }
    public List<DevelopmentClassloader> findStaleClassLoaders() {

        Map<String, PluginInfo> infos = new HashMap<>();


        for (DevelopmentClassloader classloader : classloaders.values()) {
            if(classloader.isStaleSources() || classloader.isFailed()) {
                infos.put(classloader.getPluginInfo().getPluginId(), classloader.getPluginInfo());
            }
        }




        // Add plugins we provide services to
        for (PluginInfo info : new ArrayList<>(infos.values())) {
            Map<String, PluginInfo> deps = new HashMap<>();
            getServiceConsumingPlugins(info, deps, new ArrayList<>(getPluginInfos()));
            for (String id : deps.keySet()) {
                infos.put(id, deps.get(id));
            }
        }

        for (PluginInfo info : new ArrayList<>(infos.values())) {
            Map<String, PluginInfo> deps = new HashMap<>();
            getChildPlugins(info, deps, new ArrayList<>(getPluginInfos()));
            for (String id : deps.keySet()) {
                infos.put(id, deps.get(id));
            }
        }

        List<PluginInfo> sorted = PluginInfo.resolveClassloaderOrder(new ArrayList<>(infos.values()));

        Collections.sort(sorted, new Comparator<PluginInfo>() {
            @Override
            public int compare(PluginInfo o1, PluginInfo o2) {
                return isDevPlugin(o1) ? -1 : isDevPlugin(o2) ? -1 : 1;
            }

            private boolean isDevPlugin(PluginInfo o1) {
                return o1.getPluginId().contains(":reststop-development-plugin");
            }
        });
        Map<String, DevelopmentClassloader> sortedLoaders = new LinkedHashMap<>();

        for (PluginInfo pluginInfo : sorted) {

            sortedLoaders.put(pluginInfo.getPluginId(), classloaders.get(pluginInfo.getPluginId()));
        }

        return new ArrayList<>(sortedLoaders.values());

    }

    private void getChildPlugins(PluginInfo info, Map<String, PluginInfo> children, List<PluginInfo> all) {

        for (PluginInfo child : info.getChildren(all)) {
            if(!children.containsKey(child.getPluginId())) {
                children.put(child.getPluginId(), child);
                getChildPlugins(child, children, all);
            }
        }
    }
}
