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
import org.kantega.reststop.core.*;
import org.kantega.reststop.servlet.api.ServletBuilder;
import org.kantega.reststop.classloaderutils.BuildSystem;
import org.kantega.reststop.classloaderutils.PluginClassLoader;
import org.kantega.reststop.classloaderutils.PluginInfo;

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
    private Collection<PluginMutator> mutators;

    public RedeployFilter(DefaultReststopPluginManager pluginManager, ServletBuilder servletBuilder, VelocityEngine velocityEngine, boolean shouldRunTests, Collection<PluginMutator> mutators) {
        this.pluginManager = pluginManager;
        this.servletBuilder = servletBuilder;
        this.velocityEngine = velocityEngine;
        this.shouldRunTests = shouldRunTests;
        this.mutators = mutators;
        PluginInfo pluginInfo = ((PluginClassLoader) getClass().getClassLoader()).getPluginInfo();
        this.shadowClassLoader = (DevelopmentClassloader) DevelopmentClassLoaderFactory.getInstance().createPluginClassLoader(pluginInfo, getClass().getClassLoader().getParent(), Collections.emptyList());

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


            List<DevelopmentClassloader> staleClassLoaders = new ArrayList<>();

            List<PluginInfo> needsBuild = new ArrayList<>();

            List<PluginMutator>  activeMutators = new ArrayList<>();

            synchronized (this) {


                needsBuild.addAll(needsBuild());

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


                staleClassLoaders.addAll(findStaleClassLoaders());



                if(! staleClassLoaders.isEmpty()){

                    StandardJavaFileManager fileManager = DevelopmentClassloader.compiler.getStandardFileManager(null, null, null);

                    try {
                        for (DevelopmentClassloader classloader : staleClassLoaders) {
                            try {

                                classloader.setFailed(true);

                                classloader.compileSources(fileManager);
                                classloader.copySourceResorces();
                                classloader.compileJavaTests(fileManager);
                                classloader.copyTestResources();


                            } catch (JavaCompilationException e) {
                                new ErrorReporter(velocityEngine, classloader.getBasedir()).addCompilationException(e).render(req, resp);
                                return;
                            }

                        }
                    } finally {
                        try {
                            fileManager.close();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
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
                                .map(cl -> (PluginClassLoader)cl)
                                .map(PluginClassLoader::getPluginInfo)
                                .collect(Collectors.toList());
                        pluginManager.deploy(all, factory);
                    } else {
                        pluginManager.deploy(stale, factory);
                    }


                    if (shouldRunTests) {
                        /*
                        Map<String, DevelopmentClassloader> testLoaders = new LinkedHashMap<>();

                        for (DevelopmentClassloader classloader : newClassLoaders) {
                            testLoaders.put(classloader.getPluginInfo().getPluginId(), classloader);
                        }

                        for (DevelopmentClassloader classloader : newClassLoaders) {

                            if (!testLoaders.containsKey(classloader.getPluginInfo().getPluginId())) {
                                boolean stale = classloader.isStaleTests();
                                if (stale) {
                                    classloader.compileJavaTests();
                                    classloader.copyTestResources();
                                }
                                if (stale || classloader.hasFailingTests()) {
                                    testLoaders.put(classloader.getPluginInfo().getPluginId(), classloader);
                                }

                            }
                        }

                        for (DevelopmentClassloader classloader : testLoaders.values()) {
                            try {
                                synchronized (runTestsMonitor) {

                                    List<Class> testClasses = classloader.getTestClasses();
                                    if (testClasses.size() > 0) {
                                        Class[] objects = testClasses.toArray(new Class[testClasses.size()]);
                                        ClassLoader loader = Thread.currentThread().getContextClassLoader();
                                        Thread.currentThread().setContextClassLoader(testClasses.get(0).getClassLoader());
                                        try {


                                            Result result = new JUnitCore().run(objects);
                                            if (result.getFailureCount() > 0) {
                                                classloader.testsFailed();
                                                throw new TestFailureException(result.getFailures());
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
                    */

                    }

                }

                mutators.stream()
                        .filter(PluginMutator::possibleUpdatePluginState)
                        .forEach(activeMutators::add);

            }

            if(!needsBuild.isEmpty() || !staleClassLoaders.isEmpty() || ! activeMutators.isEmpty()) {
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







    public List<DevelopmentClassloader> findStaleClassLoaders() {
        return Stream.concat(pluginManager.getPluginClassLoaders().stream()
                .filter(cl -> cl instanceof DevelopmentClassloader)
                .map(cl -> (DevelopmentClassloader) cl), Stream.of(shadowClassLoader))
                .filter(cl -> cl.isStaleSources() || cl.isFailed())
                .collect(Collectors.toList());
    }

    @Override
    public void destroy() {

    }

}
