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
