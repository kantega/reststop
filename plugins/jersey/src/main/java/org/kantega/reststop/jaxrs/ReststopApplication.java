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
import org.kantega.reststop.jaxrsapi.JaxRsPlugin;

import javax.ws.rs.core.Application;
import java.util.*;

/**
 *
 */
public class ReststopApplication extends Application {

    private final Collection<ReststopPlugin> plugins;

    public ReststopApplication(Collection<ReststopPlugin> plugins) {
        this.plugins = plugins;
    }

    public ReststopApplication() {
        plugins = null;
    }

    @Override
    public Set<Object> getSingletons() {
        Set<Object> singletons = new HashSet<>();
        if(plugins != null) {
            for(ReststopPlugin plugin : plugins) {
                if(plugin instanceof JaxRsPlugin) {
                    JaxRsPlugin jaxRsPlugin = JaxRsPlugin.class.cast(plugin);
                    for (Application application : jaxRsPlugin.getJaxRsApplications()) {
                        singletons.addAll(application.getSingletons());
                    }
                }
            }
        }
        return singletons;
    }

    @Override
    public Set<Class<?>> getClasses() {
        Set<Class<?>> classes = new HashSet<>();
        if(plugins != null) {
            for(ReststopPlugin plugin : plugins) {
                if(plugin instanceof JaxRsPlugin) {
                    JaxRsPlugin jaxRsPlugin = JaxRsPlugin.class.cast(plugin);
                    for (Application application : jaxRsPlugin.getJaxRsApplications()) {
                        classes.addAll(application.getClasses());
                    }
                }
            }
        }
        return classes;
    }

    @Override
    public Map<String, Object> getProperties() {
        Map<String, Object> props = new HashMap<String, Object>();
        if(plugins != null) {
            for(ReststopPlugin plugin : plugins) {
                if(plugin instanceof JaxRsPlugin) {
                    JaxRsPlugin jaxRsPlugin = JaxRsPlugin.class.cast(plugin);
                    for (Application application : jaxRsPlugin.getJaxRsApplications()) {
                        props.putAll(application.getProperties());
                    }
                }
            }
        }
        return props;
    }
}
