package org.kantega.reststop.api;

import org.kantega.reststop.api.jaxws.JaxWsPlugin;

import java.util.Collection;

/**
 *
 */
public interface ReststopPluginManager {

    Collection<ReststopPlugin> getPlugins();

    <T extends ReststopPlugin> Collection<T> getPlugins(Class<T> pluginClass);

    ClassLoader getClassLoader(ReststopPlugin plugin);
}
