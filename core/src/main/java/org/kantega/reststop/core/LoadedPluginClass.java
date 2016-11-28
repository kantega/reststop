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

import org.kantega.reststop.api.PluginExport;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

/**
 *
 */
public class LoadedPluginClass {
    private final Object plugin;
    private final PluginClassInfo pluginClassInfo;
    private final List<Method> preDestroyMethods;
    private final Collection<PluginExport> exports;

    public LoadedPluginClass(Object plugin, Collection<PluginExport> exports, PluginClassInfo pluginClassInfo, List<Method> preDestroyMethods) {
        this.plugin = plugin;
        this.exports = exports;
        this.pluginClassInfo = pluginClassInfo;
        this.preDestroyMethods = preDestroyMethods;
    }

    public Object getPlugin() {
        return plugin;
    }

    public Collection<PluginExport> getExports() {
        return exports;
    }

    public PluginClassInfo getPluginClassInfo() {
        return pluginClassInfo;
    }

    public List<Method> getPreDestroyMethods() {
        return preDestroyMethods;
    }

    @Override
    public String toString() {
        return "{ class: " +getPluginClassInfo().getPluginClass().getName() +", plugin: " + getPluginClassInfo().getClassLoader().getPluginInfo().getPluginId() + " }";
    }
}
