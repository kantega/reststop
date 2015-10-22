/*
 * Copyright 2015 Kantega AS
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

import org.kantega.jexmec.ServiceKey;
import org.kantega.jexmec.ServiceLocator;
import org.kantega.jexmec.ctor.ClassLocator;
import org.kantega.jexmec.ctor.ConstructorInjectionPluginLoader;
import org.kantega.jexmec.ctor.InvalidPluginException;
import org.kantega.reststop.api.Config;
import org.kantega.reststop.classloaderutils.PluginClassLoader;

import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

/**
 *
 */
public class ReststopPluginLoader extends ConstructorInjectionPluginLoader<Object> {
    private final ReststopInitializer.PluginExportsServiceLocator reststopServiceLocator;

    public ReststopPluginLoader(ReststopInitializer.PluginExportsServiceLocator reststopServiceLocator) {

        this.reststopServiceLocator = reststopServiceLocator;
    }

    @Override
    public List<Object> loadPlugins(Class<Object> pluginClass, ClassLoader classLoader, ServiceLocator serviceLocator) {
        ClassLocator<Class<Object>> classLocator = new ClassLocator<>("META-INF/services/ReststopPlugin/simple.txt");

        final List<Object> plugins = new ArrayList<>();

        final List<Class<Object>> pluginClasses = classLocator.locateClasses(classLoader);


        for (Class clazz : pluginClasses) {
            if (clazz.getDeclaredConstructors().length != 1) {
                throw new InvalidPluginException("Plugin class " + clazz.getName() + " must have exactly one constructor", clazz);
            }
            final Constructor constructor = clazz.getDeclaredConstructors()[0];
            final Object[] params = getConstructorParameters(constructor, serviceLocator, classLoader);

            try {

                final Object plugin = pluginClass.cast(constructor.newInstance(params));
                plugins.add(plugin);
            } catch (InstantiationException e) {
                final String msg = "Plugin class " + clazz.getName() + " is an " + (clazz.isInterface() ? "interface" : "abstract class");
                throw new InvalidPluginException(msg, e, clazz);
            } catch (IllegalAccessException e) {
                throw new InvalidPluginException("Plugin class " + clazz.getName() + " or its constructor has an illegal access modifier", e, clazz);
            } catch (InvocationTargetException e) {
                throw new InvalidPluginException("Plugin class " + clazz.getName() + " threw an exeception during construction ", e, clazz);
            }
        }
        return plugins;
    }

    private Object[] getConstructorParameters(Constructor constructor, ServiceLocator serviceLocator, ClassLoader classLoader) {

        Object[] params = new Object[constructor.getParameterTypes().length];
        for (int i = 0; i < constructor.getParameterTypes().length; i++) {
            Class<?> paramClass = constructor.getParameterTypes()[i];

            if(constructor.getParameters()[i].isAnnotationPresent(Config.class)) {
                params[i] = getConfigProperty(constructor.getParameters()[i], classLoader);
            } else if(paramClass == Collection.class) {
                ParameterizedType parameterizedType = (ParameterizedType) constructor.getGenericParameterTypes()[i];
                if(parameterizedType != null) {
                    Class type = (Class) parameterizedType.getActualTypeArguments()[0];
                    Collection multiple = reststopServiceLocator.getMultiple(ServiceKey.by(type));
                    params[i] = multiple;

                }
            } else {
                if (!serviceLocator.keySet().contains(ServiceKey.by(paramClass))) {
                    throw new InvalidPluginException("Plugin  class " + constructor.getDeclaringClass() + " has an illegal constructor. Parameter " + i + " of type " + paramClass.getName() + " could not be resolved to an application service", constructor.getDeclaringClass());
                }
                params[i] = serviceLocator.get(ServiceKey.by(paramClass));
            }

        }
        return params;
    }

    private Object getConfigProperty(Parameter param, ClassLoader loader) {

        PluginClassLoader pluginClassLoader = (PluginClassLoader) loader;
        Properties properties = pluginClassLoader.getPluginInfo().getConfig();

        Config config = param.getAnnotation(Config.class);

        String name = config.property();

        if( name == null || name.trim().isEmpty())  {
            name = param.getName();
        }
        String defaultValue = config.defaultValue().isEmpty() ? null : config.defaultValue();

        String value = properties.getProperty(name, defaultValue);

        if( (value == null || value.trim().isEmpty()) && config.required()) {
            throw new IllegalArgumentException("Configuration missing for required @Config parameter '" +param.getName() +"' in class " + param.getDeclaringExecutable().getDeclaringClass().getName());
        }
        Object convertedValue = convertValue(param, value, param.getType());

        return convertedValue;


    }

    private Object convertValue(Parameter parameter, String value, Class<?> type) {
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
        throw new IllegalArgumentException("Could not convert @Config for unknown type " + parameter.getType().getName() + " of parameter '" +parameter.getName() +"' in class " + parameter.getDeclaringExecutable().getDeclaringClass().getName());
    }
}
