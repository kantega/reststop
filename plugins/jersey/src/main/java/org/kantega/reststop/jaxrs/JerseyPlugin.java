package org.kantega.reststop.jaxrs;

import org.glassfish.jersey.jackson.JacksonFeature;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.servlet.ServletProperties;
import org.kantega.reststop.api.*;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.MediaType;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 *
 */
public class JerseyPlugin extends DefaultReststopPlugin {


    private ServletContainer filter;

    public JerseyPlugin(final Reststop reststop, final ReststopPluginManager pluginManager) throws ServletException {


        addPluginListener(new PluginListener() {
            @Override
            public void pluginsUpdated(Collection<ReststopPlugin> plugins) {
                reloadFromPlugins(plugins);
            }

            @Override
            public void pluginManagerStarted() {
                reloadFromPlugins(pluginManager.getPlugins());
            }

            private void reloadFromPlugins(Collection<ReststopPlugin> plugins) {
                synchronized (JerseyPlugin.this) {
                    try {
                        if(filter == null) {
                            filter = addJerseyFilter( new ReststopApplication(plugins));
                            filter.init(reststop.createFilterConfig("jersey", new Properties()));

                            addServletFilter(reststop.createFilter(filter, "/*", FilterPhase.USER));
                        } else {
                            filter.reload(getResourceConfig(new ReststopApplication(plugins)));
                        }
                    } catch (ServletException e) {
                        throw new RuntimeException(e);
                    }

                }

            }
        });
    }

    private ServletContainer addJerseyFilter(Application application) {
        ResourceConfig resourceConfig = getResourceConfig(application);

        return new ServletContainer(resourceConfig) {
            @Override
            public void doFilter(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws IOException, ServletException {
                // Force read of request parameters if specified, otherwise Jersey will eat them
                if(request.getMethod().equals("POST") && request.getContentType().equals(MediaType.APPLICATION_FORM_URLENCODED)) {
                    request.getParameterMap();
                }
                super.doFilter(request, response, chain);
            }
        };
    }


    private ResourceConfig getResourceConfig(Application application) {
        ResourceConfig resourceConfig = ResourceConfig.forApplication(application);
        resourceConfig.register(JacksonFeature.class);
        Map<String, Object> props = new HashMap<>(resourceConfig.getProperties());
        props.put(ServletProperties.FILTER_FORWARD_ON_404, "true");
        resourceConfig.setProperties(props);

        return resourceConfig;
    }
}
