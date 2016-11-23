/*
 * Copyright 2016 Kantega AS
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

package org.kantega.reststop.core2.bootstrap;

import org.kantega.reststop.bootstrap.Bootstrap;
import org.kantega.reststop.classloaderutils.*;
import org.kantega.reststop.core2.DefaultReststopPluginManager;
import org.kantega.reststop.core2.PluginClassInfo;
import org.w3c.dom.Document;

import java.io.File;
import java.net.MalformedURLException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.kantega.reststop.classloaderutils.PluginInfo.configure;

/**
 *
 */
@SuppressWarnings("Duplicates")
public class DefaultBootstrap implements Bootstrap{

    private DefaultReststopPluginManager manager;

    @Override
    public void bootstrap(File globalConfigurationFile, Document pluginsXml, File repositoryDirectory) {
        List<PluginInfo> parsed = PluginInfo.parse(pluginsXml);
        configure(parsed, globalConfigurationFile);

        manager = new DefaultReststopPluginManager();

        deployPlugins(parsed, repositoryDirectory, getClass().getClassLoader());

        manager.getPluginClassLoaders().stream()
                .map(cl -> (PluginClassLoader)cl)
                .filter(cl -> cl.getPluginInfo().getArtifactId().equals("reststop-cxf-plugin"))
                .forEach(cl ->
                        manager.redeploy(Collections.singletonList(cl), Function.identity())
                );
    }

    private void deployPlugins(List<PluginInfo> plugins, File repositoryDirectory, ClassLoader parentClassLoader) {
        Map<String, PluginClassLoader> byDep = new HashMap<>();


        List<PluginInfo> pluginsInClassloaderOrder = PluginInfo.resolveClassloaderOrder(plugins);

        for (PluginInfo info : pluginsInClassloaderOrder) {

            if (info.isDirectDeploy()) {
                PluginClassLoader pluginClassloader = new PluginClassLoader(info, getParentClassLoader(info, parentClassLoader, byDep));

                File pluginJar = getPluginFile(info, repositoryDirectory);

                try {
                    pluginClassloader.addURL(pluginJar.toURI().toURL());
                    info.setFile(pluginJar);

                    for (Artifact artifact : info.getClassPath("runtime")) {
                        pluginClassloader.addURL(getPluginFile(artifact, repositoryDirectory).toURI().toURL());

                    }
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }


                byDep.put(info.getGroupIdAndArtifactId(), pluginClassloader);

            }
        }

        List<PluginInfo> pluginsInStartupOrder = PluginInfo.resolveStartupOrder(pluginsInClassloaderOrder);

        List<PluginClassLoader> classLoaders = pluginsInStartupOrder.stream()
                .map(p -> byDep.get(p.getGroupIdAndArtifactId()))
                .collect(Collectors.toList());


        manager.deploy(classLoaders);
    }

    private ClassLoader getParentClassLoader(PluginInfo pluginInfo, ClassLoader parentClassLoader, Map<String, PluginClassLoader> byDep) {
        Set<PluginClassLoader> delegates = new HashSet<>();

        for (Artifact dep : pluginInfo.getDependsOn()) {
            PluginClassLoader dependencyLoader = byDep.get(dep.getGroupIdAndArtifactId());
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

    private File getPluginFile(Artifact artifact, File repoDir) {
        if (repoDir != null) {
            return new File(repoDir,
                    artifact.getGroupId().replace('.', '/') + "/"
                            + artifact.getArtifactId() + "/"
                            + artifact.getVersion() + "/"
                            + artifact.getArtifactId() + "-" + artifact.getVersion() + ".jar");

        } else {
            return artifact.getFile();
        }
    }


    @Override
    public void shutdown() {
         manager.stop();
    }
}
