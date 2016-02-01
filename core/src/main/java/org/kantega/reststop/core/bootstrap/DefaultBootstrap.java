/*
 * Copyright 2016 Kantega AS
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

package org.kantega.reststop.core.bootstrap;

import org.kantega.reststop.bootstrap.Bootstrap;
import org.kantega.reststop.classloaderutils.PluginInfo;
import org.kantega.reststop.core.PluginManagerBuilder;
import org.w3c.dom.Document;

import java.io.File;
import java.util.List;

import static org.kantega.reststop.classloaderutils.PluginInfo.configure;

/**
 *
 */
public class DefaultBootstrap implements Bootstrap{


    private PluginManagerBuilder.DefaultReststopPluginManager manager;

    private static PluginManagerBuilder.PluginInfosClassLoaderProvider createClassLoaderProvider(File globalConfigurationFile, Document pluginsXml, File repositoryDirectory) {

        List<PluginInfo> parsed = PluginInfo.parse(pluginsXml);
        configure(parsed, globalConfigurationFile);
        return new PluginManagerBuilder.PluginInfosClassLoaderProvider(parsed, repositoryDirectory);

    }


    @Override
    public void bootstrap(File globalConfigurationFile, Document pluginsXml, File repositoryDirectory) {
        PluginManagerBuilder.PluginInfosClassLoaderProvider classLoaderProvider = createClassLoaderProvider(globalConfigurationFile, pluginsXml, repositoryDirectory);

        manager = PluginManagerBuilder.builder()
                .withClassLoaderProviders(classLoaderProvider)
                .build();
    }

    @Override
    public void shutdown() {
        manager.stop();
    }
}
