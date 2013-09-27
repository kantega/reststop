package org.kantega.reststop.jaxrs;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.servlet.ServletProperties;
import org.glassfish.jersey.servlet.WebFilterConfig;
import org.kantega.reststop.api.*;

import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.ws.rs.core.Application;
import java.util.Collection;
import java.util.Enumeration;

import static java.util.Collections.emptyEnumeration;
import static java.util.Collections.singletonMap;

/**
 *
 */
public class JerseyPlugin extends DefaultReststopPlugin {


    public JerseyPlugin(Reststop reststop, final ReststopPluginManager pluginManager, ServletContext servletContext) {

        final ServletContainer filter = addJerseyFilter( new ReststopApplication());
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            filter.init(new EmptyConfig(servletContext));
        } catch (ServletException e) {
            throw new RuntimeException("Exception starting Jersey", e);
        } finally {
            Thread.currentThread().setContextClassLoader(loader);
        }

        addServletFilter(reststop.createFilter(filter, "/*", FilterPhase.USER));

        addPluginListener(new PluginListener() {
            @Override
            public void pluginsUpdated(Collection<ReststopPlugin> plugins) {
                if(filter != null) {
                    filter.reload(getResourceConfig(new ReststopApplication(pluginManager)));
                }
            }
        });
    }

    private ServletContainer addJerseyFilter(Application application) {
        ResourceConfig resourceConfig = getResourceConfig(application);
        ServletContainer container = new ServletContainer(resourceConfig);

        return container;
    }


    private ResourceConfig getResourceConfig(Application application) {
        ResourceConfig resourceConfig = ResourceConfig.forApplication(application);
        resourceConfig.setProperties(singletonMap(ServletProperties.FILTER_FORWARD_ON_404, "true"));

        return resourceConfig;
    }

    private class EmptyConfig implements FilterConfig {
        private final ServletContext servletContext;

        public EmptyConfig(ServletContext servletContext) {

            this.servletContext = servletContext;
        }

        @Override
        public String getFilterName() {
            return "jersey";
        }

        @Override
        public ServletContext getServletContext() {
            return servletContext;
        }

        @Override
        public String getInitParameter(String s) {
            return null;
        }

        @Override
        public Enumeration<String> getInitParameterNames() {
            return emptyEnumeration();
        }
    }
}
