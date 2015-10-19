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
import org.kantega.reststop.api.ReststopPlugin;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 *
 */
public class ReststopPluginLoader extends ConstructorInjectionPluginLoader<ReststopPlugin> {
    private final ReststopInitializer.PluginExportsServiceLocator reststopServiceLocator;

    public ReststopPluginLoader(ReststopInitializer.PluginExportsServiceLocator reststopServiceLocator) {

        this.reststopServiceLocator = reststopServiceLocator;
    }

    @Override
    public List<ReststopPlugin> loadPlugins(Class<ReststopPlugin> pluginClass, ClassLoader classLoader, ServiceLocator serviceLocator) {
        ClassLocator<Class<ReststopPlugin>> classLocator = createPluginClassLocator(pluginClass);
        final List<ReststopPlugin> plugins = new ArrayList<>();

        final List<Class<ReststopPlugin>> pluginClasses = classLocator.locateClasses(classLoader);


        for (Class clazz : pluginClasses) {
            if (!pluginClass.isAssignableFrom(clazz)) {
                throw new InvalidPluginException("Plugin class " + clazz.getName() + " is not an instance of " + pluginClass.getName(), clazz);
            }
            if (clazz.getDeclaredConstructors().length != 1) {
                throw new InvalidPluginException("Plugin class " + clazz.getName() + " must have exactly one constructor", clazz);
            }
            final Constructor constructor = clazz.getDeclaredConstructors()[0];
            final Object[] params = getConstructorParameters(constructor, serviceLocator);

            try {

                final ReststopPlugin plugin = pluginClass.cast(constructor.newInstance(params));
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

    private Object[] getConstructorParameters(Constructor constructor, ServiceLocator serviceLocator) {

        Object[] params = new Object[constructor.getParameterTypes().length];
        for (int i = 0; i < constructor.getParameterTypes().length; i++) {
            Class<?> paramClass = constructor.getParameterTypes()[i];

            if(paramClass == Collection.class) {
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
}
