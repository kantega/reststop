/*
 * Copyright 2015 Kantega AS
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

import javax.servlet.Filter;
import javax.servlet.ServletContext;
import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.kantega.reststop.classloaderutils.PluginInfo.configure;

/**
 *
 */
@Plugin
public class DevelopmentPlugin  {

    @Export
    private final Collection<Filter> filters  = new ArrayList<>();

    @Export
    private VelocityEngine velocityEngine;

    private volatile boolean providerStarted = false;

    @Export
    private Collection<PluginListener> listeners = new ArrayList<>();

    public DevelopmentPlugin(@Config(defaultValue = "true") String runTestsOnRedeploy,
                             final Reststop reststop, ServletContext servletContext) {

        Document pluginsXml = (Document) servletContext.getAttribute("pluginsXml");


        if(! loadedByDevelopmentClassLoader()) {

            for(PluginInfo info : PluginInfo.parse(pluginsXml)) {

                if(info.isDevelopmentPlugin()) {
                    ClassLoader pluginParentClassLoader = reststop.getPluginParentClassLoader();
                    PluginInfo origInfo = ((PluginClassLoader)getClass().getClassLoader()).getPluginInfo();
                    final DevelopmentClassloader devloader = createClassLoader(origInfo, pluginParentClassLoader);

                    listeners.add(new PluginListener() {
                        @Override
                        public void pluginManagerStarted() {
                            reststop.changePluginClassLoaders().remove(getClass().getClassLoader()).add(devloader).commit();
                        }
                    });
                    return;
                }
            }

            throw new IllegalStateException("Can't seem to find myself");
        }


        velocityEngine = initVelocityEngine();

        final DevelopmentClassLoaderProvider provider = new DevelopmentClassLoaderProvider();


        List<PluginInfo> infos = PluginInfo.parse(pluginsXml);
        String pluginConfigurationDirectory = servletContext.getInitParameter("pluginConfigurationDirectory");
        String applicationName = servletContext.getInitParameter("applicationName");
        File globalConfigFile = new File(pluginConfigurationDirectory, applicationName +".conf");
        configure(infos, globalConfigFile);
        List<PluginInfo> sortedInfos = PluginInfo.resolveStartupOrder(infos);
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
            listeners.add(new PluginListener() {
                @Override
                public void pluginManagerStarted() {
                    provider.start(reststop);
                }
            });
        } else {
            listeners.add(new PluginListener() {
                @Override
                public void pluginsUpdated(Collection<Object> plugins) {
                    if (!providerStarted) {
                        providerStarted = true;
                        for (Object plugin : plugins) {
                            if (plugin == DevelopmentPlugin.this) {
                                provider.start(reststop);
                            }
                        }
                    }
                }
            });
        }




        filters.add(reststop.createFilter(new DevelopmentAssetsFilter(), "/dev/assets/*", FilterPhase.PRE_UNMARSHAL));
        filters.add(reststop.createFilter(new RedeployFilter(provider, reststop, velocityEngine, "true".equals(runTestsOnRedeploy)), "/*", FilterPhase.PRE_UNMARSHAL));

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

        return new DevelopmentClassloader(info, info.getSourceDirectory(), info.getFile(), compile, runtime, test, pluginParentClassLoader);
    }

    private boolean loadedByDevelopmentClassLoader() {
        return getClass().getClassLoader().getClass().getName().equals(DevelopmentClassloader.class.getName());
    }


}
