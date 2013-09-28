package org.kantega.reststop.classloaderutils;

import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandlerFactory;

/**
 *
 */
public class PluginClassLoader extends URLClassLoader {

    private final PluginInfo pluginInfo;

    public PluginClassLoader(PluginInfo pluginInfo, ClassLoader parent) {
        this(pluginInfo, new URL[0], parent);
    }

    public PluginClassLoader(PluginInfo pluginInfo, URL[] urls, ClassLoader parent) {
        super(urls, parent);
        this.pluginInfo = pluginInfo;
    }

    public PluginClassLoader(PluginInfo pluginInfo, URL[] urls) {
        super(urls);
        this.pluginInfo = pluginInfo;
    }

    public PluginClassLoader(PluginInfo pluginInfo, URL[] urls, ClassLoader parent, URLStreamHandlerFactory factory) {
        super(urls, parent, factory);
        this.pluginInfo = pluginInfo;
    }

    public PluginInfo getPluginInfo() {
        return pluginInfo;
    }

    @Override
    public void addURL(URL url) {
        super.addURL(url);
    }
}
