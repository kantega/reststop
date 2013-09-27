package org.kantega.reststop.api;

import java.util.Collection;

/**
 *
 */
public interface ReststopPluginManager {

    Collection<ReststopPlugin> getPlugins();

    <T extends ReststopPlugin> Collection<T> getPlugins(Class<T> pluginClass);


}
