package org.kantega.reststop.core;

import org.kantega.reststop.classloaderutils.PluginClassLoader;
import org.kantega.reststop.classloaderutils.PluginInfo;

import java.util.List;

public interface ClassLoaderFactory {
    PluginClassLoader createPluginClassLoader(PluginInfo pluginInfo, ClassLoader parentClassLoader, List<PluginInfo> allPlugins);
}
