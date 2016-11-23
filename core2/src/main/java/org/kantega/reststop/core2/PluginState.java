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

import org.kantega.reststop.api.PluginExport;
import org.kantega.reststop.classloaderutils.PluginClassLoader;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 */
public class PluginState {

    // Real state
    private final List<LoadedPluginClass> plugins;
    private final Map<Class, Object> staticServices;

    // Computed for lookup
    private final Map<PluginClassLoader, ClassLoader> classLoaders;
    private final Map<Object, ClassLoader> byClassLoader;
    private final Map<Class, List<PluginExport>> exports;
    private final Map<Class, List<Object>> services;
    private final List<Object> allPlugins;

    public PluginState(Map<Class, Object> services) {
        this(Collections.emptyList(), services);
    }

    public PluginState(List<LoadedPluginClass> plugins, Map<Class, Object> staticServices) {
        this.plugins = plugins;
        this.staticServices = staticServices;


        this.classLoaders = plugins.stream()
                .map(LoadedPluginClass::getPluginClassInfo)
                .map(PluginClassInfo::getClassLoader)
                .collect(Collectors.toMap(Function.identity(), Function.identity(), (u,v) -> u, IdentityHashMap::new));

        this.byClassLoader = Collections.unmodifiableMap(plugins.stream()
                .collect(Collectors.toMap(LoadedPluginClass::getPlugin, p -> p.getPluginClassInfo().getClassLoader())));

        Map<Class, List<PluginExport>> exports = plugins.stream()
                .map(LoadedPluginClass::getExports)
                .flatMap(Collection::stream)
                .collect(Collectors.groupingBy(PluginExport::getType));

        this.exports = Collections.unmodifiableMap(exports);

        Map<Class, List<Object>> services = new HashMap<>();

        staticServices.forEach( (type, o) -> services.computeIfAbsent(type, t -> new ArrayList<>()).add(o));

        this.exports.values().stream()
                .flatMap(List::stream)
                .forEach(e -> services.computeIfAbsent(e.getType(), t -> new ArrayList<>()).add(e.getExport()));

        this.services = Collections.unmodifiableMap(services);

        this.allPlugins = Collections.unmodifiableList(plugins.stream()
                .map(LoadedPluginClass::getPlugin)
                .collect(Collectors.toList()));
    }



    public PluginState addPlugin(LoadedPluginClass loadedPluginClass) {
        return new PluginState(Stream.concat(plugins.stream(), Stream.of(loadedPluginClass)).collect(Collectors.toList()), staticServices);
    }

    public PluginState removePlugin(LoadedPluginClass plugin) {
        return new PluginState(plugins.stream().filter(p -> p != plugin).collect(Collectors.toList()), staticServices);
    }

    public Collection<Object> getPlugins() {
        return allPlugins;
    }

    public <T> Collection<T> getPlugins(Class<T> pluginClass) {
        return allPlugins.stream()
                .filter(p -> pluginClass.isAssignableFrom(p.getClass()))
                .map(p -> (T) p)
                .collect(Collectors.toList());
    }

    public ClassLoader getClassLoader(Object plugin) {
        return byClassLoader.get(plugin);
    }

    public Collection<PluginClassLoader> getClassLoaders() {
        return classLoaders.keySet();
    }


    public <T> Collection<T> findExports(Class<T> type) {
        Collection<T> exports = (Collection<T>) this.exports.get(type);
        return exports == null ? Collections.emptyList() : exports;
    }

    public <T> Collection<T> getServices(Class<T> type) {
        return (Collection<T>) services.get(type);
    }

    public boolean hasService(Class<?> type) {
        return services.containsKey(type);
    }

    public <T> T getService(Class<T> type) {
        List<Object> objects = services.get(type);
        return objects.isEmpty() ? null : (T) objects.get(0);
    }

    public List<LoadedPluginClass> getPluginsLoadedBy(Collection<PluginClassLoader> classLoaders) {
        Set<Integer> hashCodes = classLoaders.stream()
                .map(System::identityHashCode)
                .collect(Collectors.toSet());
        return this.plugins.stream()
                .filter(p -> hashCodes.contains(System.identityHashCode(p.getPluginClassInfo().getClassLoader())))
                .collect(Collectors.toList());
    }

    public List<LoadedPluginClass> findConsumers(Set<Class> exportedTypes) {
        List<LoadedPluginClass> directConsumers = plugins.stream()
                .filter(p -> p.getPluginClassInfo().getImports().stream().anyMatch(exportedTypes::contains))
                .collect(Collectors.toList());

        return findTransitiveConsumers(directConsumers);
    }

    private List<LoadedPluginClass> findTransitiveConsumers(List<LoadedPluginClass> consumers ) {

        Map<LoadedPluginClass, LoadedPluginClass> transitiveConsumers = new IdentityHashMap<>();

        for (LoadedPluginClass consumer : consumers) {
            findTransitiveConsumers(consumer, transitiveConsumers);
        }

        return new ArrayList<>(transitiveConsumers.keySet());
    }

    private void findTransitiveConsumers(LoadedPluginClass producer, Map<LoadedPluginClass, LoadedPluginClass> transitiveConsumers) {
        plugins.stream()
                .filter(p -> isConsumer(p, producer))
                .forEach(p -> findTransitiveConsumers(p, transitiveConsumers));
        transitiveConsumers.put(producer, producer);

    }

    private boolean isConsumer(LoadedPluginClass consumer, LoadedPluginClass producer) {
        return producer.getExports().stream()
                .map(PluginExport::getType)
                .anyMatch(t -> consumer.getPluginClassInfo().getImports().contains(t));
    }

    public List<PluginClassLoader> getTransitiveClosure(Collection<PluginClassLoader> classLoaders) {

        Map<PluginClassLoader, PluginClassLoader> transitiveConsumers = new IdentityHashMap<>();

        classLoaders.forEach(cl -> getTransitiveClosure(cl, transitiveConsumers));

        return new ArrayList<>(transitiveConsumers.keySet());
    }

    private void getTransitiveClosure(PluginClassLoader classLoader, Map<PluginClassLoader, PluginClassLoader> transitiveConsumers) {
        List<PluginClassLoader> dependants = classLoaders.keySet().stream()
                .filter(cl -> cl.getPluginInfo().getDependsOn().stream().anyMatch(a -> a.getPluginId().equals(classLoader.getPluginInfo().getPluginId())))
                .collect(Collectors.toList());

        dependants.forEach(cl -> getTransitiveClosure(cl, transitiveConsumers));

        transitiveConsumers.put(classLoader, classLoader);
    }
}