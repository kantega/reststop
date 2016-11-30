package org.kantega.reststop.assets;

import org.kantega.reststop.api.*;
import org.kantega.reststop.classloaderutils.PluginClassLoader;
import org.kantega.reststop.servlet.api.FilterPhase;
import org.kantega.reststop.servlet.api.ServletBuilder;

import javax.servlet.Filter;
import java.util.Collection;

/**
 *
 */
@Plugin
public class AssetsPlugin {

    @Export
    private final Filter assetFilter;

    public AssetsPlugin(ServletBuilder servletBuilder, Collection<PluginClassLoader> pluginClassLoaders,
                        @Config(defaultValue = "/assets/") String assetFilterMapping,
                        @Config(defaultValue = "assets/") String assetFilterClassPathPrefix) {
        AssetFilter filter = new AssetFilter(pluginClassLoaders, assetFilterClassPathPrefix, assetFilterMapping);

        assetFilter = servletBuilder.filter(filter, FilterPhase.USER, assetFilterMapping +"*");
    }
}
