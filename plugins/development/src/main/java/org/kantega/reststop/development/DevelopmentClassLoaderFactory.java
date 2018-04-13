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

package org.kantega.reststop.development;

import org.kantega.reststop.classloaderutils.Artifact;
import org.kantega.reststop.classloaderutils.PluginClassLoader;
import org.kantega.reststop.classloaderutils.PluginInfo;
import org.kantega.reststop.core.ClassLoaderFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class DevelopmentClassLoaderFactory implements ClassLoaderFactory {

    private static DevelopmentClassLoaderFactory instance;

    public static DevelopmentClassLoaderFactory getInstance() {
        return instance;
    }

    public DevelopmentClassLoaderFactory() {
        instance = this;
    }

    @Override
    public PluginClassLoader createPluginClassLoader(PluginInfo pluginInfo, ClassLoader parentClassLoader, List<PluginInfo> allPlugins) {

        List<File> runtime = getClasspath(pluginInfo, allPlugins, "runtime", true);
        List<File> compile = getClasspath(pluginInfo, allPlugins,  "compile", false);
        List<File> test = getClasspath(pluginInfo, allPlugins,  "test", false);
        File sourceDir = pluginInfo.getSourceDirectory();

        DevelopmentClassloader classloader = new DevelopmentClassloader(pluginInfo, sourceDir, pluginInfo.getFile(),
                compile,
                runtime,
                test,
                parentClassLoader, 1);

        return classloader;
    }

    private List<File> getClasspath(PluginInfo pluginInfo, List<PluginInfo> allPlugins, String scope, boolean filterParentPlugins) {
        List<File> files = new ArrayList<>();
        for (Artifact artifact : pluginInfo.getClassPath(scope)) {
            if(!filterParentPlugins || allPlugins.stream().noneMatch(p -> p.getPluginId().equals(artifact.getPluginId()))) {
                files.add(artifact.getFile());
            }
        }
        return files;
    }
}
