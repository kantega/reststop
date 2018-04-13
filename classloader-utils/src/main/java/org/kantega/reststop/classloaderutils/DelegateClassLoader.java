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

package org.kantega.reststop.classloaderutils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 *
 */
public class DelegateClassLoader extends ClassLoader {
    private final PluginClassLoader[] delegates;

    private ConcurrentMap<String, Class> loadedClasses = new ConcurrentHashMap<>();

    private Set<String> usedDelegates = new CopyOnWriteArraySet<>();

    public DelegateClassLoader(ClassLoader parentClassLoader, Set<PluginClassLoader> delegates) {
        super(parentClassLoader);
        this.delegates = delegates.toArray(new PluginClassLoader[delegates.size()]);
    }


    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {

        Class<?> loadedClass = loadedClasses.get(name);
        if(loadedClass != null) {
            return loadedClass;
        }
        try {
            return getParent().loadClass(name);
        } catch (ClassNotFoundException e) {

            for (PluginClassLoader delegate : delegates) {
                try {
                    Class<?> aClass = delegate.loadClass(name);
                    loadedClasses.putIfAbsent(aClass.getName(), aClass);
                    usedDelegates.add(delegate.getPluginInfo().getPluginId());
                    return aClass;
                } catch (ClassNotFoundException e1) {

                }

            }
            throw new ClassNotFoundException(name);
        }
    }

    @Override
    protected URL findResource(String name) {
        URL resource = getParent().getResource(name);
        if (resource != null) {
            return resource;
        }
        for (ClassLoader delegate : delegates) {
            resource = delegate.getResource(name);
            if (resource != null) {
                return resource;
            }
        }
        return null;
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        Enumeration<URL> parentResources = getParent().getResources(name);
        List<URL> delegateResources = null;
        for (ClassLoader delegate : delegates) {
            Enumeration<URL> resources = delegate.getResources(name);
            if(resources != null ) {
                if(delegateResources == null) {
                    delegateResources = new LinkedList<URL>();
                }
                delegateResources.addAll(Collections.list(resources));
            }

        }
        if(delegateResources == null) {
            return parentResources;
        } else {
            LinkedList<URL> urls = new LinkedList<URL>();
            if(parentResources != null) {
                urls.addAll(Collections.list(parentResources));
            }
            urls.addAll(delegateResources);
            return Collections.enumeration(urls);
        }
    }


    public boolean isParentUsed(PluginInfo parent) {
        return usedDelegates.contains(parent.getPluginId());
    }
}
