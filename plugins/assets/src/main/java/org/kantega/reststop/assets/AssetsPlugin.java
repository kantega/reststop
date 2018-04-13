/*
 * Copyright 2018 Kantega AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
