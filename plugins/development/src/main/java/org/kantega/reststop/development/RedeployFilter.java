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

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;
import org.junit.runner.notification.RunListener;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

/**
 *
 */
public class RedeployFilter implements Filter {

    private final DevelopmentClassLoaderProvider provider;
    private volatile boolean testing = false;

    private final Object compileSourcesMonitor = new Object();
    private final Object compileTestsMonitor = new Object();
    private final Object runTestsMonitor = new Object();

    public RedeployFilter(DevelopmentClassLoaderProvider provider) {
        this.provider = provider;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        HttpServletResponse resp = (HttpServletResponse) servletResponse;

        DevelopmentClassloader classloader = provider.getClassloader();

        if (!testing &&  !req.getRequestURI().startsWith("/assets")) {
            try {

                boolean staleSources;

                synchronized (compileSourcesMonitor) {
                    staleSources = classloader.isStaleSources();
                    if (staleSources) {
                        System.out.println("Needs redeploy!");
                        provider.redeploy();
                    }
                }

                boolean staleTests = classloader.isStaleTests();
                if (staleSources || staleTests) {

                    synchronized (compileTestsMonitor) {
                        classloader.compileJavaTests();
                        classloader.copyTestResources();
                    }

                    try {
                        testing = true;
                        synchronized (runTestsMonitor) {
                            List<Class> testClasses = provider.getClassloader().getTestClasses();
                            Class[] objects = testClasses.toArray(new Class[testClasses.size()]);
                            Result result = new JUnitCore().run(objects);
                            if (result.getFailureCount() > 0) {
                                Failure first = result.getFailures().get(0);
                                throw new RuntimeException(first.getMessage(), first.getException());
                            }
                        }
                    } finally {
                        testing = false;
                    }
                }
            } catch (JavaCompilationException e) {
                new ErrorReporter(classloader.getBasedir()).addCompilationException(e).render(req, resp);
                return;
            }
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void destroy() {

    }

}
