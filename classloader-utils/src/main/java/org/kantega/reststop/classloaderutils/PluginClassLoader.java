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

package org.kantega.reststop.classloaderutils;

import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandlerFactory;

/**
 *
 */
public class PluginClassLoader extends URLClassLoader {

    private final PluginInfo pluginInfo;

    private final long creationTime;

    public PluginClassLoader(PluginInfo pluginInfo, ClassLoader parent) {
        this(pluginInfo, new URL[0], parent);
    }

    public PluginClassLoader(PluginInfo pluginInfo, URL[] urls, ClassLoader parent) {
        super(urls, parent);
        this.pluginInfo = pluginInfo;
        creationTime = System.currentTimeMillis();
    }

    public PluginClassLoader(PluginInfo pluginInfo, URL[] urls) {
        super(urls);
        this.pluginInfo = pluginInfo;
        creationTime = System.currentTimeMillis();
    }

    public PluginClassLoader(PluginInfo pluginInfo, URL[] urls, ClassLoader parent, URLStreamHandlerFactory factory) {
        super(urls, parent, factory);
        this.pluginInfo = pluginInfo;
        creationTime = System.currentTimeMillis();
    }

    @Override
    public String toString() {
        return "PluginClassLoader for " + pluginInfo.getPluginId();
    }

    public PluginInfo getPluginInfo() {
        return pluginInfo;
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }

    public long getCreationTime() {
        return creationTime;
    }

    public Class<?> loadClassWithoutParent(String name) throws ClassNotFoundException {
        Class<?> c = findLoadedClass(name);
        if(c == null) {
            c = findClass(name);
        }
        if(c == null) {
            throw  new ClassNotFoundException(name);
        } else {
            return c;
        }

    }
}
