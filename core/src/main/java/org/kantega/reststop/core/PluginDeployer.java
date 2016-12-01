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

package org.kantega.reststop.core;

import org.kantega.reststop.classloaderutils.*;

import java.io.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 */
public class PluginDeployer {

    private final ReststopPluginLoader pluginLoader;
    private final ClassLoader parentClassLoader;

    public PluginDeployer(ClassLoader parentClassLoader, File configFile) {
        this.parentClassLoader = parentClassLoader;
        pluginLoader = new ReststopPluginLoader(configFile);
    }

    private PluginState deploy(List<PluginClassInfo> plugins,PluginState currentPluginState) {

        List<PluginClassInfo> startupOrder = PluginClassInfo.resolveStartupOrder(plugins);
        PluginState pluginState = currentPluginState;

        for (PluginClassInfo pluginClassInfo : startupOrder) {
            pluginState = pluginState.addPlugin(pluginLoader.loadPlugin(pluginClassInfo, pluginState));
        }
        return pluginState;
    }

    public PluginState deploy(Collection<PluginInfo> plugins, ClassLoaderFactory classLoaderFactory, PluginState currentPluginState) {

        Map<String, PluginInfo> byId = plugins.stream().collect(Collectors.toMap(PluginInfo::getPluginId, Function.identity()));

        List<PluginClassLoader> replaceRequest = currentPluginState.getClassLoaders().stream()
                .filter(cl -> byId.containsKey(cl.getPluginInfo().getPluginId()))
                .collect(Collectors.toList());

        List<PluginInfo> addRequest = plugins.stream()
                .filter(p -> ! currentPluginState.getPluginInfosById().containsKey(p.getPluginId()))
                .collect(Collectors.toList());


        List<PluginClassLoader> remove = currentPluginState.getTransitiveClosure(replaceRequest);

        List<LoadedPluginClass> removedPlugins = currentPluginState.getPluginsLoadedBy(remove);

        Stream<PluginInfo> replaced = remove.stream()
                .map(PluginClassLoader::getPluginInfo)
                .map(p -> byId.getOrDefault(p.getPluginId(), p));

        List<PluginInfo> newClassLoaderPluginInfos = Stream.concat(replaced,
                addRequest.stream()).collect(Collectors.toList());


        List<PluginClassLoader> newClassLoaders = newClassLoaders(newClassLoaderPluginInfos, currentPluginState, classLoaderFactory);
        List<PluginClassInfo> newPlugins = findPlugins(newClassLoaders);

        Set<Class> affectedTypes = getAffectedTypes(removedPlugins, newPlugins);
        affectedTypes.add(PluginClassLoader.class);

        List<LoadedPluginClass> affectedPlugins = currentPluginState.findConsumers(affectedTypes);

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

        pluginState = pluginState.removeClassLoaders(remove);

        pluginState = pluginState.addPluginClassLoaders(newClassLoaders);

        pluginState = deploy(deploys, pluginState);


        return pluginState;
    }

    private List<PluginClassLoader> newClassLoaders(Collection<PluginInfo> pluginInfos, PluginState currentPluginState, ClassLoaderFactory classLoaderFactory) {
        Map<String, PluginClassLoader> current = currentPluginState.getClassLoaders().stream()
                .collect(Collectors.toMap(p -> p.getPluginInfo().getGroupIdAndArtifactId(), p ->p));


        List<PluginClassLoader> result = new ArrayList<>();

        for (PluginInfo info : PluginInfo.resolveClassloaderOrder(new ArrayList<>(pluginInfos))) {
            ClassLoader parentClassLoader = getParentClassLoader(info, this.parentClassLoader, current);
            List<PluginInfo> allPlugins = current.values().stream().map(PluginClassLoader::getPluginInfo).collect(Collectors.toList());
            PluginClassLoader newClassLoader = classLoaderFactory.createPluginClassLoader(info, parentClassLoader, allPlugins);
            current.put(newClassLoader.getPluginInfo().getGroupIdAndArtifactId(), newClassLoader);
            result.add(newClassLoader);
            Iterator<ClassLoaderFactory> classLoaderFactories = ServiceLoader.load(ClassLoaderFactory.class, newClassLoader).iterator();
            if(classLoaderFactories.hasNext()) {
                classLoaderFactory= classLoaderFactories.next();
            }
        }

        return result;
    }

    public ClassLoader getParentClassLoader(PluginInfo pluginInfo, ClassLoader parentClassLoader, Map<String, PluginClassLoader> byDep) {
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
                    return name.startsWith("META-INF/services/ReststopPlugin/")
                            || name.equals("META-INF/services/" + ClassLoaderFactory.class.getName());
                }
            };
        }
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

    public PluginState undeploy(Collection<PluginInfo> plugins, PluginState currentPluginState) {
        List<PluginClassLoader> classLoaders = currentPluginState.getClassLoaders().stream()
                .filter(cl -> plugins.stream().anyMatch(p -> cl.getPluginInfo().getPluginId().equals(p.getPluginId())))
                .collect(Collectors.toList());

        List<PluginClassLoader> transitiveClosure = currentPluginState.getTransitiveClosure(classLoaders);

        PluginState pluginState = undeploy(currentPluginState.getPluginsLoadedBy(transitiveClosure), currentPluginState);

        return pluginState.removeClassLoaders(new ArrayList<>(transitiveClosure));
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

        return classLoaders.stream()
                .map(this::findPlugins)
                .flatMap(List::stream)
                .collect(Collectors.toList());

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
                pluginLoader.findConsumedPropertyNames(pluginClass),
                pluginLoader.findConsumedTypes(pluginClass),
                pluginLoader.findExportedTypes(pluginClass));
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

    public PluginState restart(List<LoadedPluginClass> plugins, PluginState pluginState) {
        pluginState = undeploy(plugins, pluginState);
        return  deploy(plugins.stream().map(LoadedPluginClass::getPluginClassInfo).collect(Collectors.toList()), pluginState);
    }
}
