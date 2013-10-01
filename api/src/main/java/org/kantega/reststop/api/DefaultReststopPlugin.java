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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *
 */
public class DefaultReststopPlugin implements ReststopPlugin {

    private final List<Filter> servletFilters = new CopyOnWriteArrayList<>();
    private final List<PluginListener> pluginListeners = new CopyOnWriteArrayList<>();
    private final Map<Class<?>, Object> services = new ConcurrentHashMap<>();

    protected void addServletFilter(Filter filter) {
        servletFilters.add(filter);
    }

    public List<Filter> getServletFilters() {
        return servletFilters;
    }

    protected  <T> T addService(T service) {
        Class<T> type = (Class<T>) service.getClass();
        return addService(type, service);
    }

    protected <T> T addService(Class<T> type, T service) {
        if(services.containsKey(type)) {
            throw new IllegalArgumentException("Service already added with type " + type.getName());
        }
        services.put(type, service);
        return service;
    }


    @Override
    public <T> T getService(Class<T> type) {
        return type.cast(services.get(type));
    }

    @Override
    public Set<Class<?>> getServiceTypes() {
        return services.keySet();
    }

    @Override
    public Collection<PluginListener> getPluginListeners() {
        return pluginListeners;
    }

    protected void addPluginListener(PluginListener pluginListener) {
        pluginListeners.add(pluginListener);
    }


}
