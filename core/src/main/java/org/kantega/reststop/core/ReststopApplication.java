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

package org.kantega.reststop.core;

import org.kantega.jexmec.PluginManager;
import org.kantega.reststop.api.ReststopPlugin;

import javax.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;

/**
 *
 */
public class ReststopApplication extends Application {

    private final PluginManager<ReststopPlugin> pluginManager;

    public ReststopApplication(PluginManager<ReststopPlugin> pluginManager) {
        this.pluginManager = pluginManager;
    }

    @Override
    public Set<Object> getSingletons() {
        Set<Object> singletons = new HashSet<>();
        for(ReststopPlugin plugin : pluginManager.getPlugins()) {
            singletons.addAll(plugin.getJaxRsSingletonResources());
        }
        return singletons;
    }

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<>();
        for(ReststopPlugin plugin : pluginManager.getPlugins()) {
            classes.addAll(plugin.getJaxRsContainerClasses());
        }
        return classes;
    }
}