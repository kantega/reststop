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
import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.kantega.reststop.api.Reststop;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 *
 */
public class RedeployFilter implements Filter {

    private final DevelopmentClassLoaderProvider provider;
    private final Reststop reststop;
    private volatile boolean testing = false;

    private final Object compileSourcesMonitor = new Object();
    private final Object compileTestsMonitor = new Object();
    private final Object runTestsMonitor = new Object();
    private final VelocityEngine velocityEngine;

    public RedeployFilter(DevelopmentClassLoaderProvider provider, Reststop reststop, VelocityEngine velocityEngine) {
        this.provider = provider;
        this.reststop = reststop;
        this.velocityEngine = velocityEngine;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        HttpServletResponse resp = (HttpServletResponse) servletResponse;

        Map<String, DevelopmentClassloader> classloaders = provider.getClassloaders();
        for (String pluginId : classloaders.keySet()) {
            DevelopmentClassloader classloader = classloaders.get(pluginId);

            if (!testing &&  !req.getRequestURI().startsWith("/assets")) {
                try {



                    synchronized (compileSourcesMonitor) {
                        if (classloader.isStaleSources()) {
                            classloader = provider.redeploy(pluginId, classloader);
                            req.setAttribute("wasStaleSources", Boolean.TRUE);
                            reststop.newFilterChain(filterChain).doFilter(servletRequest, servletResponse);
                            return;
                        }
                    }

                    boolean staleTests = classloader.isStaleTests();
                    boolean wasStaleSources = req.getAttribute("wasStaleSources") != null;
                    if (wasStaleSources || staleTests || classloader.hasFailingTests()) {

                        synchronized (compileTestsMonitor) {
                            classloader.compileJavaTests();
                            classloader.copyTestResources();
                        }

                        synchronized (runTestsMonitor) {
                            if(!this.testing) {
                                try {
                                    this.testing = true;
                                    List<Class> testClasses = classloader.getTestClasses();
                                    Class[] objects = testClasses.toArray(new Class[testClasses.size()]);
                                    Result result = new JUnitCore().run(objects);
                                    if (result.getFailureCount() > 0) {
                                        classloader.testsFailed();
                                        throw new TestFailureException(result.getFailures());
                                    } else {
                                        classloader.testsPassed();
                                    }
                                } finally {
                                    this.testing = false;
                                }
                            }
                        }
                    }
                } catch (JavaCompilationException e) {
                    new ErrorReporter(velocityEngine, classloader.getBasedir()).addCompilationException(e).render(req, resp);
                    return;
                } catch (TestFailureException e) {
                    new ErrorReporter(velocityEngine, classloader.getBasedir()).addTestFailulreException(e).render(req, resp);
                    return;
                }
            }

        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {

    }

}
