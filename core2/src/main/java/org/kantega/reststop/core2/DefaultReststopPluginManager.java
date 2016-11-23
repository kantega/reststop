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
import org.kantega.reststop.api.ReststopPluginManager;
import org.kantega.reststop.classloaderutils.PluginClassLoader;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 *
 */
public class DefaultReststopPluginManager implements ReststopPluginManager {



    private final PluginDeployer pluginDeployer = new PluginDeployer();

    private volatile PluginState pluginState;


    public DefaultReststopPluginManager() {
        this(Collections.emptyMap());
    }

    public DefaultReststopPluginManager(Map<Class, Object> staticServices) {
        Map<Class, Object> services = new HashMap<>(staticServices);
        services.put(ReststopPluginManager.class, this);
        pluginState = new PluginState(services);
    }

    public synchronized void stop() {
        pluginState = pluginDeployer.undeploy(pluginState.getClassLoaders(), pluginState);
    }

    public synchronized void deploy(Collection<PluginClassLoader> classLoaders) {
        pluginState = pluginDeployer.deploy(classLoaders, pluginState);
    }

    public synchronized void redeploy(Collection<PluginClassLoader> remove, Function<PluginClassLoader, PluginClassLoader> replacedClassLoader) {
        pluginState = pluginDeployer.redeploy(remove, replacedClassLoader, pluginState);
    }

    @Override
    public <T> Collection<T> findExports(Class<T> type) {
        return pluginState.findExports(type);
    }

    @Override
    public Collection<Object> getPlugins() {
        return pluginState.getPlugins();
    }

    @Override
    public <T> Collection<T> getPlugins(Class<T> pluginClass) {
        return pluginState.getPlugins(pluginClass);
    }

    @Override
    public ClassLoader getClassLoader(Object plugin) {
        return pluginState.getClassLoader(plugin);
    }

    @Override
    public Collection<ClassLoader> getPluginClassLoaders() {
        return pluginState.getClassLoaders().stream()
                .map(cl -> (ClassLoader)cl)
                .collect(Collectors.toList());
    }

    @Override
    public <T> Collection<PluginExport<T>> findPluginExports(Class<T> type) {
        return pluginState.findExports((Class)type);
    }
}
