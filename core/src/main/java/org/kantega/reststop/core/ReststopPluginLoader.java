/*
 * Copyright 2018 Kantega AS
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

import org.kantega.reststop.api.Config;
import org.kantega.reststop.api.Export;
import org.kantega.reststop.api.PluginExport;
import org.kantega.reststop.classloaderutils.PluginClassLoader;
import org.kantega.reststop.classloaderutils.PluginInfo;

import javax.annotation.PreDestroy;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *
 */
@SuppressWarnings("Duplicates")
public class ReststopPluginLoader {

    private static Pattern sysPropPattern = Pattern.compile(".*\\$\\{(.*)\\}.*");
    private final File configFile;

    public ReststopPluginLoader(File configFile) {

        this.configFile = configFile;
    }


    public LoadedPluginClass loadPlugin(PluginClassInfo pluginClassInfo, PluginState pluginState) {

        Class clazz = pluginClassInfo.getPluginClass();

        List<Method> preDestroyMethods = findPredestroyMethods(clazz);

        Properties config = readConfig(pluginClassInfo.getClassLoader().getPluginInfo());

        final Constructor constructor = clazz.getDeclaredConstructors()[0];

        final Object[] params = getConstructorParameters(constructor, pluginState, config);


        Object plugin = withClassloaderContext(pluginClassInfo.getClassLoader(), () -> {
            try {
                return constructor.newInstance(params);
            } catch (InstantiationException e) {
                final String msg = "Plugin class " + clazz.getName() + " is an " + (clazz.isInterface() ? "interface" : "abstract class");
                throw new InvalidPluginException(msg, e, clazz);
            } catch (IllegalAccessException e) {
                throw new InvalidPluginException("Plugin class " + clazz.getName() + " or its constructor has an illegal access modifier", e, clazz);
            } catch (InvocationTargetException e) {
                throw new InvalidPluginException("Plugin class " + clazz.getName() + " threw an exeception during construction ", e, clazz);
            }
        });

        Collection<PluginExport> exports = findExports(plugin, pluginClassInfo.getClassLoader());

        return new LoadedPluginClass(plugin, exports, pluginClassInfo, preDestroyMethods);
    }

    private Properties readConfig(PluginInfo pluginInfo) {

        File artifact = new File(configFile.getParentFile(), pluginInfo.getArtifactId() +".conf");
        File artifactVersion = new File(configFile.getParentFile(), pluginInfo.getArtifactId() +"-" + pluginInfo.getVersion() +".conf");

        Properties properties = new Properties();
        properties.putAll(pluginInfo.getConfig());

        addProperties(properties, configFile, artifact, artifactVersion);

        return properties;
    }

    private static void addProperties(Properties properties, File... files) {
        if(files != null) {
            for (File file : files) {
                if(file != null && file.exists()) {
                    Properties prop = new Properties();
                    try(FileInputStream in = new FileInputStream(file)) {
                        prop.load(in);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    properties.putAll(prop);
                }
            }
        }
    }

    public Set<Class> findConsumedTypes(Class clazz) {
        Constructor constructor = clazz.getConstructors()[0];

        Set<Class> consumedType = new HashSet<>();

        for (int i = 0; i < constructor.getParameterTypes().length; i++) {
            Class<?> paramClass = constructor.getParameterTypes()[i];

            if (constructor.getParameters()[i].isAnnotationPresent(Config.class)) {
                continue;
            }
            consumedType.add(unwrapParameterType(constructor, paramClass, i));
        }

        return consumedType;
    }

    public Set<String> findConsumedPropertyNames(Class clazz) {
        String[] parameterNames = readParameterNames(clazz);
        Constructor constructor = clazz.getConstructors()[0];

        Set<String> propertyNames = new HashSet<>();

        for (int i = 0; i < constructor.getParameterTypes().length; i++) {
            if (constructor.getParameters()[i].isAnnotationPresent(Config.class)) {
                Config config = constructor.getParameters()[i].getAnnotation(Config.class);

                if(constructor.getParameters()[i].getType() == Properties.class) {
                    continue;
                }
                String name = config.property();

                if( name == null || name.trim().isEmpty())  {
                    name = parameterNames[i];
                }
                propertyNames.add(name);
            }
        }

        return propertyNames;
    }

    public boolean isConsumingAllProperties(Class clazz) {
        Constructor constructor = clazz.getConstructors()[0];

        for (int i = 0; i < constructor.getParameterTypes().length; i++) {
            if (constructor.getParameters()[i].isAnnotationPresent(Config.class)) {
                if(constructor.getParameters()[i].getType() == Properties.class) {
                    return true;
                }
            }
        }

        return false;
    }

    private Class unwrapParameterType(Constructor constructor, Class paramClass, int i) {
        if (paramClass == Collection.class) {
            ParameterizedType parameterizedType = (ParameterizedType) constructor.getGenericParameterTypes()[i];
            if (parameterizedType.getActualTypeArguments()[0] instanceof ParameterizedType) {
                ParameterizedType nestedParameterizedType = (ParameterizedType) parameterizedType.getActualTypeArguments()[0];
                if (nestedParameterizedType.getRawType() == PluginExport.class) {
                    return (Class) nestedParameterizedType.getActualTypeArguments()[0];
                }
                throw new IllegalArgumentException("Unknown nested parameterized raw type " + nestedParameterizedType.getRawType() + " for constructor in " + constructor.getDeclaringClass());
            } else {
                return (Class) parameterizedType.getActualTypeArguments()[0];
            }
        }
        return paramClass;
    }

    private List<Method> findPredestroyMethods(Class clazz) {
        List<Method> preDestroyMethods = new ArrayList<>();

        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(PreDestroy.class)) {
                if ((method.getModifiers() & Modifier.PUBLIC) == 1 && method.getReturnType() == Void.TYPE && method.getParameters().length == 0) {
                    preDestroyMethods.add(method);
                } else {
                    throw new IllegalArgumentException("@PreDestroy annotated method " + method + " must be public void and have zero parameters");
                }

            }

        }
        return preDestroyMethods;
    }

    public Set<Class> findExportedTypes(Class clazz) {

        Set<Class> types = new HashSet<>();

        for (Field field : clazz.getDeclaredFields()) {
            if (field.getAnnotation(Export.class) != null) {
                Class<?> type = field.getType();
                if (type == Collection.class) {
                    ParameterizedType parameterizedType = (ParameterizedType) field.getGenericType();
                    Class<?> serviceKey = (Class<?>) parameterizedType.getActualTypeArguments()[0];
                    types.add(serviceKey);
                } else {
                    types.add(type);
                }
            }
        }

        return types;
    }

    public Collection<PluginExport> findExports(Object plugin, ClassLoader classLoader) {

        List<PluginExport> exports = new ArrayList<>();


        for (Field field : plugin.getClass().getDeclaredFields()) {
            if (field.getAnnotation(Export.class) != null) {
                try {
                    field.setAccessible(true);
                    Object service = field.get(plugin);
                    if (service != null) {

                        Class<?> type = field.getType();
                        if (type == Collection.class) {
                            ParameterizedType parameterizedType = (ParameterizedType) field.getGenericType();

                            Class<?> serviceKey = (Class<?>) parameterizedType.getActualTypeArguments()[0];

                            Collection<?> collection = (Collection<?>) service;
                            collection.forEach((s) -> exports.add(new Exreg(classLoader, plugin, serviceKey, s)));
                        } else {
                            exports.add(new Exreg(classLoader, plugin, type, service));
                        }

                    }
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }

            }
        }

        return exports;
    }
    public void unloadPlugin(LoadedPluginClass loadedPlugin) {
        for (Method method : loadedPlugin.getPreDestroyMethods()) {
            try {
                method.invoke(loadedPlugin.getPlugin());
            } catch (IllegalAccessException | InvocationTargetException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static <T> T withClassloaderContext(ClassLoader classLoader, Supplier<T> runnable) {
        ClassLoader current = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(classLoader);
            return runnable.get();
        } finally {
            Thread.currentThread().setContextClassLoader(current);
        }
    }

    public Class loadPluginClass(PluginClassLoader classLoader, String className) {
        try {
            Class<?> clazz = classLoader.loadClass(className);
            if (clazz.getDeclaredConstructors().length != 1) {
                throw new InvalidPluginException("Plugin class " + clazz.getName() + " must have exactly one constructor", clazz);
            }

            return clazz;
        } catch (ClassNotFoundException e) {
            throw new IllegalArgumentException("Cannot find plugin class " + className, e);
        }
    }

    private Object[] getConstructorParameters(Constructor constructor, PluginState pluginState, Properties config) {

        String[] parameterNames = readParameterNames(constructor.getDeclaringClass());
        Object[] params = new Object[constructor.getParameterTypes().length];
        for (int i = 0; i < constructor.getParameterTypes().length; i++) {
            params[i] = findInjectableService(constructor, i, pluginState, parameterNames[i], config);

        }
        return params;
    }

    private static String[] readParameterNames(Class declaringClass) {
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

    private static Object findInjectableService(Constructor constructor, int i, PluginState pluginState, String parameterName, Properties config) {
        Class<?> paramClass = constructor.getParameterTypes()[i];

        if(constructor.getParameters()[i].isAnnotationPresent(Config.class)) {
            return getConfigProperty(constructor.getParameters()[i], parameterName, config);
        }

        if(paramClass == Collection.class) {
            ParameterizedType parameterizedType = (ParameterizedType) constructor.getGenericParameterTypes()[i];
            if(parameterizedType.getActualTypeArguments()[0] instanceof ParameterizedType) {
                ParameterizedType nestedParameterizedType = (ParameterizedType) parameterizedType.getActualTypeArguments()[0];
                if (nestedParameterizedType.getRawType() == PluginExport.class) {
                    Class type = (Class) nestedParameterizedType.getActualTypeArguments()[0];
                    return pluginState.findExports(type);
                }
                throw new IllegalArgumentException("Unknown nested parameterized raw type " + nestedParameterizedType.getRawType() + " for constructor in " + constructor.getDeclaringClass());
            } else {
                Class type = (Class) parameterizedType.getActualTypeArguments()[0];
                if(type == PluginClassLoader.class) {
                    return pluginState.getClassLoaders();
                } else {
                    return pluginState.getServices(type);
                }
            }
        }
        if (!pluginState.hasService(paramClass)) {
            throw new InvalidPluginException("Plugin  class " + constructor.getDeclaringClass() + " has an illegal constructor. Parameter '" + parameterName + "' of type " + paramClass.getName() + " could not be resolved to an application service", constructor.getDeclaringClass());
        }
        return pluginState.getService(paramClass);
    }

    private static Object getConfigProperty(Parameter param, String parameterName, Properties properties) {

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

        return convertValue(param, value, param.getType());


    }

    private static String interpolate(String value) {
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
                property = System.getenv(prop);
            }
            if(property == null) {
                throw new IllegalArgumentException("Missing system property or environment variable ${" + prop +"}. Cannot interpolate '"+value+"'");
            }
            value = value.replace("${" + prop +"}", property);
        }
        return value;
    }

    private static Object convertValue(Parameter parameter, String value, Class<?> type) {
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



    private static class Exreg<T> implements PluginExport<T> {
        private final ClassLoader classLoader;
        private final Object plugin;
        private final Class<T> type;
        private final T export;

        public Exreg(ClassLoader classLoader, Object plugin, Class<T> type, T export) {
            this.classLoader = classLoader;
            this.plugin = plugin;
            this.type = type;
            this.export = export;
        }

        public Object getPlugin() {
            return plugin;
        }

        @Override
        public Class<T> getType() {
            return type;
        }

        public T getExport() {
            return export;
        }

        @Override
        public ClassLoader getClassLoader() {
            return classLoader;
        }
    }
}

