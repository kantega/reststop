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

package org.kantega.reststop.core2;

import org.kantega.reststop.classloaderutils.PluginClassLoader;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 */
public class PluginDeployer {

    private final ReststopPluginLoader pluginLoader = new ReststopPluginLoader();

    public PluginState deploy(Collection<PluginClassLoader> classLoaders, PluginState currentPluginState) {
        return deploy(findPlugins(classLoaders), currentPluginState);
    }

    public PluginState deploy(List<PluginClassInfo> plugins, PluginState currentPluginState) {

        List<PluginClassInfo> startupOrder = PluginClassInfo.resolveStartupOrder(plugins);
        PluginState pluginState = currentPluginState;

        for (PluginClassInfo pluginClassInfo : startupOrder) {
            pluginState = pluginState.addPlugin(pluginLoader.loadPlugin(pluginClassInfo, pluginState));
        }
        return pluginState;
    }

    public PluginState redeploy(Collection<PluginClassLoader> removeRequest, Function<PluginClassLoader, PluginClassLoader> replacer, PluginState currentPluginState) {

        List<PluginClassLoader> remove = currentPluginState.getTransitiveClosure(removeRequest);

        List<LoadedPluginClass> removedPlugins = currentPluginState.getPluginsLoadedBy(remove);

        List<PluginClassInfo> newPlugins = findPlugins(newClassLoaders(remove, replacer));

        List<LoadedPluginClass> affectedPlugins = currentPluginState.findConsumers(getAffectedTypes(removedPlugins, newPlugins));

        List<PluginClassInfo> affecteButNotRemovedPlugins = affectedPlugins.stream()
                .filter(p -> ! remove.contains(p.getPluginClassInfo().getClassLoader()))
                .map(LoadedPluginClass::getPluginClassInfo)
                .collect(Collectors.toList());

        List<LoadedPluginClass> undeploys = Stream.concat(affectedPlugins.stream(), removedPlugins.stream())
                .distinct()
                .collect(Collectors.toList());

        List<PluginClassInfo> deploys =  Stream.concat(affecteButNotRemovedPlugins.stream(), newPlugins.stream())
                .collect(Collectors.toList());

        PluginState pluginState = currentPluginState;

        pluginState = undeploy(undeploys, pluginState);
        
        pluginState = deploy(deploys, pluginState);

        return pluginState;
    }

    private List<PluginClassLoader> newClassLoaders(Collection<PluginClassLoader> remove, Function<PluginClassLoader, PluginClassLoader> replacer) {
        return remove.stream().map(replacer::apply).collect(Collectors.toList());
    }

    private Set<Class> getAffectedTypes(List<LoadedPluginClass> removedPlugins, List<PluginClassInfo> newPlugins) {
        Set<Class> affectedTypes = new HashSet<>();
        affectedTypes.addAll(getTypesExportedBy(getPluginInfos(removedPlugins)));
        affectedTypes.addAll(getTypesExportedBy(newPlugins));

        return affectedTypes;
    }

    private List<PluginClassInfo> getPluginInfos(List<LoadedPluginClass> plugins) {
        return plugins.stream()
                .map(LoadedPluginClass::getPluginClassInfo)
                .collect(Collectors.toList());
    }

    private Set<Class> getTypesExportedBy(List<PluginClassInfo> newPlugins) {
        return newPlugins.stream().map(PluginClassInfo::getExports).flatMap(Set::stream).collect(Collectors.toSet());
    }

    public PluginState undeploy(Collection<PluginClassLoader> classLoaders, PluginState currentPluginState) {
        return undeploy(currentPluginState.getPluginsLoadedBy(classLoaders), currentPluginState);
    }

    private PluginState undeploy(List<LoadedPluginClass> plugins, PluginState currentPluginState) {

        List<PluginClassInfo> shutdownOrder = PluginClassInfo.resolveShutdownOrder(plugins.stream()
                .map(LoadedPluginClass::getPluginClassInfo)
                .collect(Collectors.toList()));


        Map<PluginClassInfo, LoadedPluginClass> lookup = plugins.stream()
                .collect(Collectors.toMap(LoadedPluginClass::getPluginClassInfo, Function.identity(), (a, b) -> a, LinkedHashMap::new));


        PluginState pluginState = currentPluginState;
        for (PluginClassInfo info : shutdownOrder) {
            LoadedPluginClass plugin = lookup.get(info);
            pluginLoader.unloadPlugin(plugin);
            pluginState = pluginState.removePlugin(plugin);
        }
        return pluginState;
    }


    private List<PluginClassInfo> findPlugins(Collection<PluginClassLoader> classLoaders) {
        List<PluginClassInfo> pluginClasses = classLoaders.stream()
                .map(this::findPlugins)
                .flatMap(List::stream)
                .collect(Collectors.toList());

        return pluginClasses;

    }

    private List<PluginClassInfo> findPlugins(PluginClassLoader pluginClassLoader) {
        return readLines(pluginClassLoader, "META-INF/services/ReststopPlugin/simple.txt").stream()
                .map(className -> getPluginInfo(className, pluginClassLoader))
                .collect(Collectors.toList());
    }

    private PluginClassInfo getPluginInfo(String className, PluginClassLoader classLoader) {
        Class pluginClass = pluginLoader.loadPluginClass(classLoader, className);
        return new PluginClassInfo(classLoader,
                pluginClass,
                pluginLoader.findConsumedTypes(pluginClass), pluginLoader.findExportedTypes(pluginClass));
    }

    private Set<String> readLines(ClassLoader pluginClassLoader, String path) {
        InputStream stream = pluginClassLoader.getResourceAsStream(path);
        if(stream != null) {
            try(BufferedReader br = new BufferedReader(new InputStreamReader(stream, "utf-8"))) {
                return br.lines().collect(Collectors.toSet());
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            return Collections.emptySet();
        }
    }
}
