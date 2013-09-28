package org.kantega.reststop.api;


import java.util.Collection;
import java.util.List;

/**
 *
 */
public interface ReststopPluginManager {

    Collection<ReststopPlugin> getPlugins();

    <T extends ReststopPlugin> Collection<T> getPlugins(Class<T> pluginClass);

    ClassLoader getClassLoader(ReststopPlugin plugin);

    Collection<ClassLoader> getPluginClassLoaders();
}
