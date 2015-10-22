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

package org.kantega.reststop.jaxrs;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.servlet.ServletProperties;
import org.kantega.reststop.api.*;
import org.kantega.reststop.jaxrsapi.ApplicationBuilder;
import org.kantega.reststop.jaxrsapi.ApplicationDeployer;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.*;

/**
 *
 */
@Plugin
public class JerseyPlugin implements ApplicationDeployer, ApplicationBuilder {


    @Export
    private final Filter jerseyFilter;

    @Export
    private final ApplicationDeployer applicationDeployer = this;

    @Export final ApplicationBuilder applicationBuilder = this;
    private ServletContainer filter;
    private Set<Integer> currentPlugins = new HashSet<>();

    public JerseyPlugin(final Reststop reststop, final ReststopPluginManager pluginManager) throws ServletException {


        filter = addJerseyFilter(new ReststopApplication(Collections.emptyList()));

        filter.init(reststop.createFilterConfig("jersey", new Properties()));

        jerseyFilter = reststop.createFilter(filter, "/*", FilterPhase.USER);
    }

    private ServletContainer addJerseyFilter(Application application) {
        ResourceConfig resourceConfig = getResourceConfig(application);

        return new ServletContainer(resourceConfig) {
            @Override
            public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
                // Force read of request parameters if specified, otherwise Jersey will eat them
                if(request.getMethod().equals("POST") && MediaType.APPLICATION_FORM_URLENCODED.equals(request.getContentType())) {
                    request.getParameterMap();
                }
                super.doFilter(request, response, chain);
            }
        };
    }

    private void redeploy(Collection<Application> applications) {
        synchronized (JerseyPlugin.this) {
            filter.reload(getResourceConfig(new ReststopApplication(applications)));
        }

    }

    private ResourceConfig getResourceConfig(Application application) {
        ResourceConfig resourceConfig = ResourceConfig.forApplication(application);
        resourceConfig.register(JacksonFeature.class);
        Map<String, Object> props = new HashMap<>(resourceConfig.getProperties());
        props.put(ServletProperties.FILTER_FORWARD_ON_404, "true");
        resourceConfig.setProperties(props);
        return resourceConfig;
    }

    @Override
    public void deploy(Collection<Application> applications) {
        redeploy(applications);
    }

    @Override
    public Build application() {
        return new Build() {
            private Set<Object> singletons = new HashSet<>();
            private Set<Class<?>> classes = new HashSet<>();

            @Override
            public Build singleton(Object resource) {
                singletons.add(resource);
                return this;
            }

            @Override
            public Build resource(Class resource) {
                classes.add(resource);
                return this;
            }

            @Override
            public Application build() {
                return new Application() {
                    @Override
                    public Set<Class<?>> getClasses() {
                        return classes;
                    }

                    @Override
                    public Set<Object> getSingletons() {
                        return singletons;
                    }

                    @Override
                    public Map<String, Object> getProperties() {
                        return Collections.emptyMap();
                    }
                };
            }
        };
    }
}
