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

        return new ServletContainer(resourceConfig);
    }


    private ResourceConfig getResourceConfig(Application application) {
        ResourceConfig resourceConfig = ResourceConfig.forApplication(application);
        resourceConfig.setProperties(singletonMap(ServletProperties.FILTER_FORWARD_ON_404, "true"));

        return resourceConfig;
    }
}
