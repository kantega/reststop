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
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;
import org.junit.platform.launcher.listeners.TestExecutionSummary;
import org.kantega.reststop.core.LoadedPluginClass;
import org.kantega.reststop.core.PluginClassInfo;
import org.kantega.reststop.core.PluginState;
import org.kantega.reststop.servlet.api.ServletBuilder;
import org.kantega.reststop.classloaderutils.BuildSystem;
import org.kantega.reststop.classloaderutils.PluginClassLoader;
import org.kantega.reststop.classloaderutils.PluginInfo;
import org.kantega.reststop.core.DefaultReststopPluginManager;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.tools.StandardJavaFileManager;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Objects.nonNull;

/**
 *
 */
public class RedeployFilter implements Filter {


    private final DevelopmentClassloader shadowClassLoader;
    private volatile boolean checkingRedeploy = false;

    private final Object runTestsMonitor = new Object();
    private final DefaultReststopPluginManager pluginManager;
    private final ServletBuilder servletBuilder;
    private final VelocityEngine velocityEngine;
    private final boolean shouldRunTests;
    private volatile long lastConfigModified = -1;
    private volatile Properties config;

    public RedeployFilter(DefaultReststopPluginManager pluginManager, ServletBuilder servletBuilder, VelocityEngine velocityEngine, boolean shouldRunTests) {
        this.pluginManager = pluginManager;
        this.servletBuilder = servletBuilder;
        this.velocityEngine = velocityEngine;
        this.shouldRunTests = shouldRunTests;
        PluginInfo pluginInfo = ((PluginClassLoader) getClass().getClassLoader()).getPluginInfo();
        this.shadowClassLoader = (DevelopmentClassloader) DevelopmentClassLoaderFactory.getInstance().createPluginClassLoader(pluginInfo, getClass().getClassLoader().getParent(), Collections.emptyList());
        config = readConfig(pluginManager.getConfigFile());
        configChanged();
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        HttpServletResponse resp = (HttpServletResponse) servletResponse;


        if (checkingRedeploy || req.getServletPath().startsWith("/assets")) {
            filterChain.doFilter(req, resp);
            return;
        }
        try {
            checkingRedeploy = true;

            List<DevelopmentClassloader> staleClassLoaders;

            List<PluginInfo> needsBuild;

            synchronized (this) {
                needsBuild = new ArrayList<>(needsBuild());

                if(!needsBuild.isEmpty()) {

                    List<PluginInfo> all = pluginManager.getPluginClassLoaders().stream()
                            .filter(cl -> cl instanceof PluginClassLoader)
                            .map(cl -> (PluginClassLoader)cl)
                            .map(PluginClassLoader::getPluginInfo)
                            .collect(Collectors.toList());

                    for (PluginInfo pluginInfo : needsBuild) {
                        for (PluginInfo p : pluginInfo.getParents(all)) {
                            pluginInfo.addDependsOn(p);
                        }
                    }
                    pluginManager.deploy(needsBuild, DevelopmentClassLoaderFactory.getInstance());
                }


                staleClassLoaders = new ArrayList<>(findStaleClassLoaders());

                if(! staleClassLoaders.isEmpty()){

                    for (DevelopmentClassloader classloader : staleClassLoaders) {

                        StandardJavaFileManager fileManager = DevelopmentClassloader.compiler.getStandardFileManager(null, null, null);

                        try {

                            classloader.setFailed(true);

                            classloader.compileSources(fileManager);
                            classloader.copySourceResorces();
                            if (shouldRunTests) {
                                classloader.compileJavaTests(fileManager);
                                classloader.copyTestResources();
                            }
                        } catch (JavaCompilationException e) {
                            new ErrorReporter(velocityEngine, classloader.getBasedir()).addCompilationException(e).render(req, resp);
                            return;
                        } finally {
                            try {
                                fileManager.close();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }

                    DevelopmentClassLoaderFactory factory = DevelopmentClassLoaderFactory.getInstance();

                    Collection<PluginInfo> stale = staleClassLoaders.stream()
                            .map(c -> (PluginClassLoader) c)
                            .map(PluginClassLoader::getPluginInfo)
                            .collect(Collectors.toList());

                    if(stale.contains(shadowClassLoader)) {
                        List<PluginInfo> all = pluginManager.getPluginClassLoaders().stream()
                                .filter(cl -> cl instanceof PluginClassLoader)
                                .map(PluginClassLoader.class::cast)
                                .map(PluginClassLoader::getPluginInfo)
                                .collect(Collectors.toList());
                        pluginManager.deploy(all, factory);
                    } else {
                        pluginManager.deploy(stale, factory);
                    }

                    if (shouldRunTests) {
                        Collection<DevelopmentClassloader> newClassLoaders =
                                pluginManager.getPluginClassLoaders()
                                        .stream()
                                        .filter(cl -> DevelopmentClassloader.class.isAssignableFrom(cl.getClass()))
                                        .map(DevelopmentClassloader.class::cast)
                                        .filter(dcl -> stale.contains(dcl.getPluginInfo()))
                                        .collect(Collectors.toList());

                        for (DevelopmentClassloader classloader : newClassLoaders) {
                            try {
                                synchronized (runTestsMonitor) {

                                    DevelopmentClassloader.ClassLoaderAndTestClasses testsAndClassLoader = classloader.getTestsAndClassLoader();
                                    if (nonNull(testsAndClassLoader)) {
                                        ClassLoader loader = Thread.currentThread().getContextClassLoader();
                                        Thread.currentThread().setContextClassLoader(testsAndClassLoader.classLoader);
                                        try {

                                            LauncherDiscoveryRequest request = LauncherDiscoveryRequestBuilder.request()
                                                    .selectors(
                                                            testsAndClassLoader.testClasses
                                                            .stream()
                                                            .map(DiscoverySelectors::selectClass)
                                                            .collect(Collectors.toList())
                                                    )
                                                    .build();
                                            Launcher launcher = LauncherFactory.create();

                                            SummaryGeneratingListener listener = new SummaryGeneratingListener();
                                            launcher.registerTestExecutionListeners(listener);

                                            launcher.execute(request);
                                            TestExecutionSummary summary = listener.getSummary();

                                            if (summary.getTestsFailedCount() > 0) {
                                                classloader.testsFailed();
                                                throw new TestFailureException(summary.getFailures());
                                            } else {
                                                classloader.testsPassed();
                                            }
                                        } finally {
                                            Thread.currentThread().setContextClassLoader(loader);
                                        }
                                    }
                                }


                            } catch (TestFailureException e) {
                                new ErrorReporter(velocityEngine, classloader.getBasedir()).addTestFailulreException(e).render(req, resp);
                                return;
                            }

                        }
                    }

                }

            }

            Set<String> changedProps = new HashSet<>(getModifiedConfigProps());

            if(!changedProps.isEmpty()) {

                PluginState pluginState = pluginManager.getPluginState();

                List<LoadedPluginClass> configuredWith = pluginState.findConfiguredWith(changedProps);
                Set<Class> exportedTypes = getTypesExportedBy(configuredWith.stream().map(LoadedPluginClass::getPluginClassInfo).collect(Collectors.toList()));
                List<LoadedPluginClass> consumers = pluginState.findConsumers(exportedTypes);

                List<LoadedPluginClass> restarts = Stream.concat(configuredWith.stream(), consumers.stream())
                        .distinct()
                        .collect(Collectors.toList());


                pluginManager.restart(restarts);
            }

            if(!needsBuild.isEmpty() || !staleClassLoaders.isEmpty() || !changedProps.isEmpty()) {
                servletBuilder.newFilterChain(filterChain).doFilter(req, resp);
            } else {
                filterChain.doFilter(servletRequest, servletResponse);
                return;
            }

        } finally {
            checkingRedeploy = false;
        }

    }

    private Set<Class> getTypesExportedBy(List<PluginClassInfo> newPlugins) {
        return newPlugins.stream().map(PluginClassInfo::getExports).flatMap(Set::stream).collect(Collectors.toSet());
    }
    private List<PluginInfo> needsBuild() {
        BuildSystem buildSystem = BuildSystem.instance;
        if(buildSystem == null) {
            return Collections.emptyList();
        } else {
            return pluginManager.getPluginClassLoaders().stream()
                    .filter(cl -> cl instanceof PluginClassLoader)
                    .map(cl -> (PluginClassLoader)cl)
                    .filter(buildSystem::needsRefresh)
                    .map(PluginClassLoader::getPluginInfo)
                    .map(buildSystem::refresh)
                    .collect(Collectors.toList());
        }
    }

    private Set<String> getModifiedConfigProps() {
        if(configChanged()) {
            Properties newConfig = readConfig(pluginManager.getConfigFile());
            Set<String> changed = findChangedProperties(this.config, newConfig);
            this.config = newConfig;
            return changed;
        }
        return Collections.emptySet();
    }

    private Set<String> findChangedProperties(Properties config, Properties newConfig) {
        Set<String> oldNames = config.stringPropertyNames();
        Set<String> newNames = newConfig.stringPropertyNames();

        Set<String> allNames = new HashSet<>();
        allNames.addAll(oldNames);
        allNames.addAll(newNames);

        Set<String> changed = new HashSet<>();

        for (String name : allNames) {
            if(!oldNames.contains(name) || !newNames.contains(name) || !config.getProperty(name).equals(newConfig.getProperty(name))) {
                changed.add(name);
            }
        }
        return changed;
    }

    private Properties readConfig(File configFile) {
        try (InputStream is = new FileInputStream(configFile)) {
            Properties properties = new Properties();
            properties.load(is);
            return properties;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private boolean configChanged() {
        File configFile = pluginManager.getConfigFile();

        if(lastConfigModified == -1) {
            lastConfigModified = configFile.lastModified();
            return false;
        }

        long currentLastModified = configFile.lastModified();
        if(currentLastModified > lastConfigModified) {
            lastConfigModified = currentLastModified;
            return true;
        }
        return false;
    }

    public List<DevelopmentClassloader> findStaleClassLoaders() {
        return Stream.concat(
                pluginManager.getPluginClassLoaders().stream()
                        .filter(cl -> cl instanceof DevelopmentClassloader)
                        .map(cl -> (DevelopmentClassloader) cl),
                Stream.of(shadowClassLoader))
                .filter(cl -> cl.isStaleSources() || cl.isStaleTests() || cl.isFailed())
                .collect(Collectors.toList());
    }

    @Override
    public void destroy() {

    }

}
