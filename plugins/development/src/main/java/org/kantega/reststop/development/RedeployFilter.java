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
import org.kantega.reststop.api.ServletBuilder;
import org.kantega.reststop.classloaderutils.PluginClassLoader;
import org.kantega.reststop.classloaderutils.PluginInfo;
import org.kantega.reststop.core2.DefaultReststopPluginManager;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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

    public RedeployFilter(DefaultReststopPluginManager pluginManager, ServletBuilder servletBuilder, VelocityEngine velocityEngine, boolean shouldRunTests) {
        this.pluginManager = pluginManager;
        this.servletBuilder = servletBuilder;
        this.velocityEngine = velocityEngine;
        this.shouldRunTests = shouldRunTests;
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

            synchronized (this) {
                staleClassLoaders.addAll(findStaleClassLoaders());

                if (staleClassLoaders.isEmpty()) {
                    filterChain.doFilter(servletRequest, servletResponse);
                    return;
                } else {

                    for (DevelopmentClassloader classloader : staleClassLoaders) {
                        try {

                            classloader.compileSources();
                            classloader.copySourceResorces();
                            classloader.compileJavaTests();
                            classloader.copyTestResources();


                        } catch (JavaCompilationException e) {
                            new ErrorReporter(velocityEngine, classloader.getBasedir()).addCompilationException(e).render(req, resp);
                            return;
                        }

                    }

                    DevelopmentClassLoaderFactory factory = DevelopmentClassLoaderFactory.getInstance();

                    Collection<PluginClassLoader> stale = staleClassLoaders.stream()
                            .map(c -> (PluginClassLoader) c)
                            .collect(Collectors.toList());

                    if(stale.contains(shadowClassLoader)) {
                        pluginManager.redeploy(new ArrayList<>(pluginManager.getPluginClassLoaders()), factory);
                    } else {
                        pluginManager.redeploy(stale, factory);
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
            }
            if (!staleClassLoaders.isEmpty()) {
                servletBuilder.newFilterChain(filterChain).doFilter(servletRequest, servletResponse);
            } else {
                filterChain.doFilter(servletRequest, servletResponse);
            }
        } finally {
            checkingRedeploy = false;
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
