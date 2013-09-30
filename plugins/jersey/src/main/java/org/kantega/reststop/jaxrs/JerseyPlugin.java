package org.kantega.reststop.jaxrs;

import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.glassfish.jersey.servlet.ServletProperties;
import org.kantega.reststop.api.*;

import javax.servlet.ServletException;
import javax.ws.rs.core.Application;
import java.util.Collection;
import java.util.Properties;

import static java.util.Collections.singletonMap;

/**
 *
 */
public class JerseyPlugin extends DefaultReststopPlugin {


    public JerseyPlugin(Reststop reststop, final ReststopPluginManager pluginManager) {

        final ServletContainer filter = addJerseyFilter( new ReststopApplication());
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            filter.init(reststop.createFilterConfig("jersey", new Properties()));
        } catch (ServletException e) {
            throw new RuntimeException("Exception starting Jersey", e);
        } finally {
            Thread.currentThread().setContextClassLoader(loader);
        }

        addServletFilter(reststop.createFilter(filter, "/*", FilterPhase.USER));

        addPluginListener(new PluginListener() {
            @Override
            public void pluginsUpdated(Collection<ReststopPlugin> plugins) {
                filter.reload(getResourceConfig(new ReststopApplication(pluginManager)));
            }
        });
    }

    private ServletContainer addJerseyFilter(Application application) {
        ResourceConfig resourceConfig = getResourceConfig(application);

        return new ServletContainer(resourceConfig);
    }


    private ResourceConfig getResourceConfig(Application application) {
        ResourceConfig resourceConfig = ResourceConfig.forApplication(application);
        resourceConfig.setProperties(singletonMap(ServletProperties.FILTER_FORWARD_ON_404, "true"));

        return resourceConfig;
    }
}
