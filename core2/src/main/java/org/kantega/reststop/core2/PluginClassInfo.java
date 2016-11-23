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

import java.util.*;

/**
 *
 */
public class PluginClassInfo {

    private final PluginClassLoader classLoader;
    private final Class pluginClass;
    private final Set<Class> imports;
    private final Set<Class> exports;

    public PluginClassInfo(PluginClassLoader classLoader, Class pluginClass, Set<Class> imports, Set<Class> exports) {
        this.classLoader = classLoader;
        this.pluginClass = pluginClass;
        this.imports = imports;
        this.exports = exports;
    }

    public static List<PluginClassInfo> resolveStartupOrder(List<PluginClassInfo> pluginClasses) {

        Map<PluginClassInfo, PluginClassInfo> processed = new HashMap<>();

        List<PluginClassInfo> sorted = new ArrayList<>();

        for (PluginClassInfo pluginClass : pluginClasses) {
            resolveStartupOrder(pluginClass, processed, sorted, pluginClasses);
        }
        return sorted;
    }

    private static void resolveStartupOrder(PluginClassInfo pluginClass, Map<PluginClassInfo, PluginClassInfo> processed, List<PluginClassInfo> sorted, List<PluginClassInfo> allPlugins) {

        allPlugins.stream()
                .filter(p -> p.getExports().stream().anyMatch(t -> pluginClass.getImports().contains(t)))
                .forEach(p -> {
                    if(!processed.containsKey(p)) {
                        resolveStartupOrder(p, processed, sorted, allPlugins);
                        processed.put(p, p);
                    }
                });
        if(!processed.containsKey(pluginClass)) {
            sorted.add(pluginClass);
            processed.put(pluginClass, pluginClass);
        }
    }

    public static List<PluginClassInfo> resolveShutdownOrder(List<PluginClassInfo> plugins) {
        List<PluginClassInfo> reversed = new ArrayList<>(resolveStartupOrder(plugins));
        Collections.reverse(reversed);
        return reversed;
    }



    public PluginClassLoader getClassLoader() {
        return classLoader;
    }

    public Set<Class> getExports() {
        return exports;
    }

    public Set<Class> getImports() {
        return imports;
    }

    public Class getPluginClass() {
        return pluginClass;
    }
}
