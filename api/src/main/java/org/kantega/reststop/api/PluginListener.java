package org.kantega.reststop.api;

import java.util.Collection;

/**
 *
 */
public abstract class PluginListener {
    public void pluginManagerStarted() {}

    public void pluginsUpdated(Collection<ReststopPlugin> plugins) {};
}
