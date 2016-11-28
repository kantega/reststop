package org.kantega.reststop.classloaderutils;

/**
 *
 */
public abstract class BuildSystem {

    public static BuildSystem instance;

    public abstract boolean needsRefresh(PluginClassLoader pluginInfo);
    public abstract PluginInfo refresh(PluginInfo pluginInfo);
}
