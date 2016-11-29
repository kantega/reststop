package org.kantega.reststop.assets;

import org.kantega.reststop.api.*;

import javax.servlet.Filter;

/**
 *
 */
@Plugin
public class AssetsPlugin {

    @Export
    private final Filter assetFilter;

    public AssetsPlugin(ServletBuilder servletBuilder, ReststopPluginManager reststopPluginManager,
                        @Config(defaultValue = "/assets/") String assetFilterMapping,
                        @Config(defaultValue = "assets/") String assetFilterClassPathPrefix) {
        AssetFilter filter = new AssetFilter(reststopPluginManager, assetFilterClassPathPrefix, assetFilterMapping);

        assetFilter = servletBuilder.filter(filter, FilterPhase.USER, assetFilterMapping +"*");
    }
}
