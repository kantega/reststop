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

package org.kantega.reststop.api;

import javax.servlet.Filter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 *
 */
public class DefaultReststopPlugin implements ReststopPlugin {

    private final List<Object> jaxRsSingletonResources = new ArrayList<>();
    private final List<Class<?>> jaxRsContainerClasses = new ArrayList<>();
    private final List<Filter> servletFilters = new ArrayList<>();
    private final List<PluginListener> pluginListeners = new ArrayList<>();

    @Override
    public Collection<Object> getJaxRsSingletonResources() {
        return jaxRsSingletonResources;
    }

    protected void addJaxRsSingletonResource(Object resource) {
        jaxRsSingletonResources.add(resource);
    }


    protected void addServletFilter(Filter filter) {
        servletFilters.add(filter);
    }

    public List<Filter> getServletFilters() {
        return servletFilters;
    }

    @Override
    public Collection<PluginListener> getPluginListeners() {
        return pluginListeners;
    }

    protected void addPluginListener(PluginListener pluginListener) {
        pluginListeners.add(pluginListener);
    }

    @Override
    public Collection<Class<?>> getJaxRsContainerClasses() {
        return jaxRsContainerClasses;
    }

    protected void addJaxRsContainerClass(Class<?> clazz) {
        jaxRsContainerClasses.add(clazz);
    }
}
