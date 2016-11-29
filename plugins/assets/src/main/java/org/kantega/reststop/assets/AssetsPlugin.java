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

    public AssetsPlugin(ServletBuilder servletBuilder, ReststopPluginManager reststopPluginManager) {
        assetFilter = servletBuilder.filter(new AssetFilter(reststopPluginManager), FilterPhase.USER, "/assets/*");
    }
}
