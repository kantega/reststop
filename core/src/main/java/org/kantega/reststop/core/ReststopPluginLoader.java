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

package org.kantega.reststop.core;

import org.kantega.reststop.api.Export;
import org.kantega.reststop.api.PluginExport;
import org.kantega.reststop.classloaderutils.PluginClassLoader;

import javax.annotation.PreDestroy;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.*;
import java.util.*;
import java.util.function.Supplier;

/**
 *
 */
@SuppressWarnings("Duplicates")
public class ReststopPluginLoader {


    public static ThreadLocal<PluginClassInfo> beingLoaded = new ThreadLocal<>();

    public LoadedPluginClass loadPlugin(PluginClassInfo pluginClassInfo, PluginState pluginState) {

        Class clazz = pluginClassInfo.getPluginClass();

        List<Method> preDestroyMethods = findPredestroyMethods(clazz);

        final Constructor constructor = clazz.getDeclaredConstructors()[0];

        Map<Parameter, Injector> injectors = new IdentityHashMap<>();

        final Object[] params = getConstructorParameters(constructor, pluginState, pluginClassInfo, injectors);


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

        return new LoadedPluginClass(plugin, exports, pluginClassInfo, preDestroyMethods, injectors);
    }

    public Set<Class> findConsumedTypes(Class clazz) {
        Constructor constructor = clazz.getConstructors()[0];

        Set<Class> consumedType = new HashSet<>();

        for (int i = 0; i < constructor.getParameterTypes().length; i++) {
            consumedType.add(unwrapParameterType(constructor, constructor.getParameterTypes()[i], i));
        }

        return consumedType;
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
        if(paramClass == byte.class) {
            return Byte.class;
        }
        if(paramClass == short.class) {
            return Short.class;
        }
        if(paramClass == int.class) {
            return Integer.class;
        }
        if(paramClass == long.class) {
            return Long.class;
        }
        if(paramClass == float.class) {
            return Float.class;
        }
        if(paramClass == double.class) {
            return Double.class;
        }
        if(paramClass == char.class) {
            return Character.class;
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
                } else if(type == Injector.class) {
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
            throw new IllegalArgumentException("Cannot find plugin class" + className, e);
        }
    }

    private Object[] getConstructorParameters(Constructor constructor, PluginState pluginState, PluginClassInfo pluginClassInfo, Map<Parameter, Injector> injectors) {

        String[] parameterNames = readParameterNames(constructor.getDeclaringClass());
        Object[] params = new Object[constructor.getParameterTypes().length];
        for (int i = 0; i < constructor.getParameterTypes().length; i++) {
            params[i] = findInjectableService(constructor, i, pluginState, parameterNames[i], pluginClassInfo, injectors);

        }
        return params;
    }

    public String[] readParameterNames(Class declaringClass) {
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

    private static Object findInjectableService(Constructor constructor, int i, PluginState pluginState, String parameterName, PluginClassInfo pluginClassInfo, Map<Parameter, Injector> injectors) {
        Class<?> paramClass = constructor.getParameterTypes()[i];

        Parameter parameter = constructor.getParameters()[i];

        for(Injector injector : pluginState.getServices(Injector.class)) {

            Optional optional;

            beingLoaded.set(pluginClassInfo);
            try {
                optional = injector.create(parameter);
            } finally {
                beingLoaded.remove();
            }

            if(optional.isPresent()) {
                injectors.put(parameter, injector);
                return optional.get();
            }
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

