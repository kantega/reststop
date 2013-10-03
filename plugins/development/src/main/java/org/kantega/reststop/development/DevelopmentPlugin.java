/*
 * Copyright 2013 Kantega AS
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

package org.kantega.reststop.development;

import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.kantega.reststop.api.*;
import org.kantega.reststop.classloaderutils.PluginClassLoader;
import org.kantega.reststop.classloaderutils.PluginInfo;
import org.kantega.reststop.development.velocity.SectionDirective;
import org.w3c.dom.Document;

import javax.servlet.ServletContext;
import java.io.File;
import java.util.Collection;
import java.util.List;

/**
 *
 */
public class DevelopmentPlugin extends DefaultReststopPlugin {
    private volatile boolean providerStarted = false;

    public DevelopmentPlugin(final Reststop reststop, ServletContext servletContext) {

        Document pluginsXml = (Document) servletContext.getAttribute("pluginsXml");


        if(! loadedByDevelopmentClassLoader()) {

            for(PluginInfo info : PluginInfo.parse(pluginsXml)) {

                if(info.isDevelopmentPlugin()) {
                    File sourceDirectory  = info.getSourceDirectory();
                    if(sourceDirectory != null) {

                        ClassLoader pluginParentClassLoader = reststop.getPluginParentClassLoader();
                        final DevelopmentClassloader devloader = createClassLoader(info, pluginParentClassLoader);

                        addPluginListener(new PluginListener() {
                            @Override
                            public void pluginManagerStarted() {
                                reststop.changePluginClassLoaders().remove(getClass().getClassLoader()).add(devloader).commit();
                            }
                        });

                        return;
                    }

                }
            }
        }

        VelocityEngine velocityEngine = addService(initVelocityEngine());

        final DevelopmentClassLoaderProvider provider = new DevelopmentClassLoaderProvider();


        List<PluginInfo> infos = PluginInfo.parse(pluginsXml);
        List<PluginInfo> sortedInfos = PluginInfo.sortByRuntimeDependencies(infos);
        for (PluginInfo info : sortedInfos) {
            if(info.isDevelopmentPlugin()) {
                provider.addExistingClassLoader(info.getPluginId(), createClassLoader(info, reststop.getPluginParentClassLoader()));
                provider.addByDepartmentId(info, (PluginClassLoader) getClass().getClassLoader());
            }

            if(!info.isDirectDeploy()) {
                provider.addPluginInfo(info);
            }
        }

        

        if(!loadedByDevelopmentClassLoader()) {
            addPluginListener(new PluginListener() {
                @Override
                public void pluginManagerStarted() {
                    provider.start(reststop);
                }
            });
        } else {
            addPluginListener(new PluginListener() {
                @Override
                public void pluginsUpdated(Collection<ReststopPlugin> plugins) {
                    if(!providerStarted) {
                        providerStarted = true;
                        for (ReststopPlugin plugin : plugins) {
                            if(plugin == DevelopmentPlugin.this) {
                                provider.start(reststop);
                            }
                        }
                    }
                }
            });
        }




        addServletFilter(reststop.createFilter(new RedeployFilter(provider, reststop, velocityEngine), "/*", FilterPhase.UNMARSHAL));

    }

    private VelocityEngine initVelocityEngine() {
        VelocityEngine engine = new VelocityEngine();

        engine.addProperty("resource.loader", "classloader");

        engine.addProperty("classloader.resource.loader.class", ClasspathResourceLoader.class.getName());

        engine.addProperty("userdirective", SectionDirective.class.getName());
        engine.addProperty("eventhandler.include.class", "org.apache.velocity.app.event.implement.IncludeRelativePath");

        engine.init();
        return engine;
    }

    private DevelopmentClassloader createClassLoader(PluginInfo info, ClassLoader pluginParentClassLoader) {
        List<File> runtime  = info.getClassPathFiles("runtime");
        List<File> compile  = info.getClassPathFiles("compile");
        List<File> test  = info.getClassPathFiles("test");

        return new DevelopmentClassloader(info, info.getSourceDirectory(), compile, runtime, test, pluginParentClassLoader);
    }

    private boolean loadedByDevelopmentClassLoader() {
        return getClass().getClassLoader().getClass().getName().equals(DevelopmentClassloader.class.getName());
    }

}
