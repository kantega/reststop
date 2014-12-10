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


import org.kantega.reststop.classloaderutils.PluginClassLoader;

import javax.servlet.Filter;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 *
 */
public class DefaultReststopPlugin implements ReststopPlugin {

    private final List<Filter> servletFilters = new CopyOnWriteArrayList<>();
    private final List<PluginListener> pluginListeners = new CopyOnWriteArrayList<>();

    public DefaultReststopPlugin() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if(loader instanceof PluginClassLoader) {
            PluginClassLoader pluginClassLoader = (PluginClassLoader) loader;
            Properties properties = pluginClassLoader.getPluginInfo().getConfig();

            Class clazz = getClass();
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                Config config = field.getAnnotation(Config.class);
                if(config != null) {

                    String name = config.property();

                    if( name == null || name.trim().isEmpty())  {
                        name = field.getName();
                    }
                    String defaultValue = config.defaultValue().isEmpty() ? null : config.defaultValue();

                    String value = properties.getProperty(name, defaultValue);

                    if( (value == null || value.trim().isEmpty()) && config.required()) {
                        throw new IllegalArgumentException("Configuration missing for required @Config field '" +field.getName() +"' in class " + field.getDeclaringClass().getName());
                    }
                    Object convertedValue = convertValue(field, value, field.getType());

                    field.setAccessible(true);
                    try {
                        field.set(this, convertedValue);
                    } catch (IllegalAccessException e) {
                        throw new IllegalStateException(e);
                    }

                }
            }
        }
    }

    private Object convertValue(Field field, String value, Class<?> type) {
        if(type == String.class) {
            return value;
        } else if(type == byte.class || type == Byte.class) {
            return Byte.parseByte(value);
        } else if(type == short.class || type == Short.class) {
            return Short.parseShort(value);
        } else if(type == int.class || type == Integer.class) {
            return Integer.parseInt(value);
        } else if(type == long.class || type == Long.class) {
            return Long.parseLong(value);
        } else if(type == float.class || type == Float.class) {
            return Float.parseFloat(value);
        } else if(type == double.class || type == Double.class) {
            return Double.parseDouble(value);
        } else if(type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(value);
        } else if(type == char.class || type == Character.class) {
            return value.charAt(0);
        }
        throw new IllegalArgumentException("Could not convert @Config for unknown field type " + field.getType().getName() + " of field '" +field.getName() +"' in class " + field.getDeclaringClass().getName());
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
    public void destroy() {

    }
}
