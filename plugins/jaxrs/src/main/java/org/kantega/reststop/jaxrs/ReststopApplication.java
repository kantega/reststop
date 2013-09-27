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

package org.kantega.reststop.jaxrs;

import org.kantega.reststop.api.ReststopPlugin;
import org.kantega.reststop.api.ReststopPluginManager;
import org.kantega.reststop.api.jaxrs.JaxRsPlugin;

import javax.ws.rs.core.Application;
import java.util.HashSet;
import java.util.Set;

/**
 *
 */
public class ReststopApplication extends Application {

    private final ReststopPluginManager pluginManager;

    public ReststopApplication(ReststopPluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    public ReststopApplication() {
        pluginManager = null;
    }

    @Override
    public Set<Object> getSingletons() {
        Set<Object> singletons = new HashSet<>();
        if(pluginManager != null) {
            for(JaxRsPlugin plugin : pluginManager.getPlugins(JaxRsPlugin.class)) {
                for (Application application : plugin.getJaxRsApplications()) {
                    singletons.addAll(application.getSingletons());
                }
            }
        }
        return singletons;
    }

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<>();
        if(pluginManager != null) {
            for(JaxRsPlugin plugin : pluginManager.getPlugins(JaxRsPlugin.class)) {
                for (Application application : plugin.getJaxRsApplications()) {
                    classes.addAll(application.getClasses());
                }
            }
        }
        return classes;
    }
}
