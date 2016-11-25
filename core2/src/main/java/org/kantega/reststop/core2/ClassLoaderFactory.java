package org.kantega.reststop.core2;

import org.kantega.reststop.classloaderutils.PluginClassLoader;
import org.kantega.reststop.classloaderutils.PluginInfo;

import java.util.List;
import java.util.Map;

public interface ClassLoaderFactory {
    PluginClassLoader createPluginClassLoader(PluginInfo pluginInfo, ClassLoader parentClassLoader, List<PluginInfo> allPlugins);
}
