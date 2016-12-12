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
import org.kantega.reststop.api.Export;
import org.kantega.reststop.api.Plugin;
import org.kantega.reststop.jaxrsapi.ApplicationBuilder;
import org.kantega.reststop.jaxrsapi.ApplicationDeployer;
import org.kantega.reststop.servlet.api.FilterPhase;
import org.kantega.reststop.servlet.api.ServletBuilder;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.lang.reflect.Proxy;
import java.util.*;

import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

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

    public JerseyPlugin(final ServletBuilder reststop) throws ServletException {

        filter = addJerseyFilter(new ReststopApplication(emptyList()));

        String pathMapping = "/*";
        filter.init(createFilterConfig(reststop, pathMapping));

        jerseyFilter = reststop.filter(filter, FilterPhase.USER, pathMapping);
    }

    private FilterConfig createFilterConfig(ServletBuilder reststop, String pathMapping) {
        String filterName = "jersey";
        FilterConfig filterConfig = reststop.filterConfig(filterName, new Properties());

        return new MappingAwareFilterConfig(filterConfig, filterName, pathMapping);
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

    private static class MappingAwareFilterConfig implements FilterConfig {
        private final FilterConfig filterConfig;
        private final ServletContext servletContext;

        MappingAwareFilterConfig(FilterConfig filterConfig, String filterName, String pathMapping) {
            this.filterConfig = filterConfig;
            this.servletContext = createMappingAwareServletContext(filterConfig.getServletContext(), filterName, pathMapping);
        }

        @Override
        public String getFilterName() {
            return filterConfig.getFilterName();
        }

        @Override
        public ServletContext getServletContext() {
            return servletContext;
        }

        @Override
        public String getInitParameter(String name) {
            return filterConfig.getInitParameter(name);
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            return filterConfig.getInitParameterNames();
        }

        private ServletContext createMappingAwareServletContext(ServletContext filterConfig, String filterName, String pathMapping) {
            return (ServletContext) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ServletContext.class}, (proxy1, method, args) -> {
                if(method.getName().equals("getFilterRegistration") && args.length == 1 && filterName.equals(args[0])) {
                    return createFilterRegistration(pathMapping);
                }
                return method.invoke(filterConfig, args);
            });
        }

        private FilterRegistration createFilterRegistration(final String pathMapping) {
            return new FilterRegistration() {
                @Override
                public void addMappingForServletNames(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter, String... servletNames) {
                    throw new IllegalStateException("This method should not be called");
                }

                @Override
                public Collection<String> getServletNameMappings() {
                    throw new IllegalStateException("This method should not be called");
                }

                @Override
                public void addMappingForUrlPatterns(EnumSet<DispatcherType> dispatcherTypes, boolean isMatchAfter, String... urlPatterns) {
                    throw new IllegalStateException("This method should not be called");
                }

                @Override
                public Collection<String> getUrlPatternMappings() {
                    return singletonList(pathMapping);
                }

                @Override
                public String getName() {
                    throw new IllegalStateException("This method should not be called");
                }

                @Override
                public String getClassName() {
                    throw new IllegalStateException("This method should not be called");
                }

                @Override
                public boolean setInitParameter(String name, String value) {
                    throw new IllegalStateException("This method should not be called");
                }

                @Override
                public String getInitParameter(String name) {
                    throw new IllegalStateException("This method should not be called");
                }

                @Override
                public Set<String> setInitParameters(Map<String, String> initParameters) {
                    throw new IllegalStateException("This method should not be called");
                }

                @Override
                public Map<String, String> getInitParameters() {
                    throw new IllegalStateException("This method should not be called");
                }
            };
        }
    }
}
