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
import org.kantega.reststop.api.PluginExport;
import org.kantega.reststop.classloaderutils.PluginClassLoader;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
public class ReststopPluginLoader extends ConstructorInjectionPluginLoader<Object> {
    private final PluginManagerBuilder.PluginExportsServiceLocator reststopServiceLocator;

    private static Pattern sysPropPattern = Pattern.compile(".*\\$\\{(.*)\\}.*");

    public ReststopPluginLoader(PluginManagerBuilder.PluginExportsServiceLocator reststopServiceLocator) {

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

            ClassLoader current = Thread.currentThread().getContextClassLoader();

            try {
                Thread.currentThread().setContextClassLoader(classLoader);
                final Object plugin = pluginClass.cast(constructor.newInstance(params));
                plugins.add(plugin);
            } catch (InstantiationException e) {
                final String msg = "Plugin class " + clazz.getName() + " is an " + (clazz.isInterface() ? "interface" : "abstract class");
                throw new InvalidPluginException(msg, e, clazz);
            } catch (IllegalAccessException e) {
                throw new InvalidPluginException("Plugin class " + clazz.getName() + " or its constructor has an illegal access modifier", e, clazz);
            } catch (InvocationTargetException e) {
                throw new InvalidPluginException("Plugin class " + clazz.getName() + " threw an exeception during construction ", e, clazz);
            } finally {
                Thread.currentThread().setContextClassLoader(current);
            }
        }
        return plugins;
    }

    private Object[] getConstructorParameters(Constructor constructor, ServiceLocator serviceLocator, ClassLoader classLoader) {

        String[] parameterNames = readParameterNames(constructor.getDeclaringClass());
        Object[] params = new Object[constructor.getParameterTypes().length];
        for (int i = 0; i < constructor.getParameterTypes().length; i++) {
            params[i] = findInjectableService(constructor, i, serviceLocator, classLoader, parameterNames[i]);

        }
        return params;
    }

    private String[] readParameterNames(Class declaringClass) {
        try {
            InputStream in = declaringClass.getResourceAsStream(declaringClass.getSimpleName() + ".parameternames");
            if(in == null) {
                return null;
            }
            byte[] buffer = new byte[512];

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            int r;
            while((r = in.read(buffer)) != -1) {
                out.write(buffer, 0, r);
            }
            in.close();

            return new String(out.toByteArray()).split(",");

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Object findInjectableService(Constructor constructor, int i, ServiceLocator serviceLocator, ClassLoader classLoader, String parameterName) {
        Class<?> paramClass = constructor.getParameterTypes()[i];

        if(constructor.getParameters()[i].isAnnotationPresent(Config.class)) {
            return getConfigProperty(constructor.getParameters()[i], parameterName, classLoader);
        }

        if(paramClass == Collection.class) {
            ParameterizedType parameterizedType = (ParameterizedType) constructor.getGenericParameterTypes()[i];
            if(parameterizedType.getActualTypeArguments()[0] instanceof ParameterizedType) {
                ParameterizedType nestedParameterizedType = (ParameterizedType) parameterizedType.getActualTypeArguments()[0];
                if (nestedParameterizedType.getRawType() == PluginExport.class) {
                    Class type = (Class) nestedParameterizedType.getActualTypeArguments()[0];
                    return reststopServiceLocator.getPluginExports(ServiceKey.by(type));
                }
                throw new IllegalArgumentException("Unknown nested parameterized raw type " + nestedParameterizedType.getRawType() + " for constructor in " + constructor.getDeclaringClass());
            } else {
                Class type = (Class) parameterizedType.getActualTypeArguments()[0];
                Collection multiple = reststopServiceLocator.getMultiple(ServiceKey.by(type));
                return multiple;
            }
        }
        if (!serviceLocator.keySet().contains(ServiceKey.by(paramClass))) {
            throw new InvalidPluginException("Plugin  class " + constructor.getDeclaringClass() + " has an illegal constructor. Parameter '" + parameterName + "' of type " + paramClass.getName() + " could not be resolved to an application service", constructor.getDeclaringClass());
        }
        return serviceLocator.get(ServiceKey.by(paramClass));
    }

    private Object getConfigProperty(Parameter param, String parameterName, ClassLoader loader) {

        PluginClassLoader pluginClassLoader = (PluginClassLoader) loader;
        Properties properties = pluginClassLoader.getPluginInfo().getConfig();

        Config config = param.getAnnotation(Config.class);

        if(param.getType() == Properties.class) {
            Properties clone = new Properties();
            clone.putAll(properties);
            return clone;
        }
        String name = config.property();

        if( name == null || name.trim().isEmpty())  {
            name = parameterName;
        }
        String defaultValue = config.defaultValue().isEmpty() ? null : config.defaultValue();

        String value = properties.getProperty(name, defaultValue);

        if( (value == null || value.trim().isEmpty()) && config.required()) {
            throw new IllegalArgumentException("Configuration missing for required @Config parameter '" +name +"' in class " + param.getDeclaringExecutable().getDeclaringClass().getName());
        }

        if (value != null) {
            value = interpolate(properties.getProperty(name, defaultValue));
        }

        Object convertedValue = convertValue(param, value, param.getType());

        return convertedValue;


    }

    private String interpolate(String value) {
        int start = 0;

        Set<String> props = new HashSet<>();

        Matcher matcher = sysPropPattern.matcher(value);
        while(matcher.find(start)) {
            String name = matcher.group(1);
            props.add(name);
            start = matcher.end();
        }
        for (String prop : props) {
            String property = System.getProperty(prop);
            if(property == null) {
                throw new IllegalArgumentException("Missing system property ${" + prop +"}");
            }
            value = value.replace("${" + prop +"}", property);
        }
        return value;
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
