package org.kantega.reststop.cxf;

import org.apache.cxf.transport.servlet.CXFNonSpringServlet;
import org.apache.cxf.wsdl.WSDLManager;
import org.kantega.reststop.api.*;
import org.kantega.reststop.jaxwsapi.EndpointConfiguration;
import org.kantega.reststop.jaxwsapi.EndpointConfigurationBuilder;
import org.kantega.reststop.jaxwsapi.JaxWsPlugin;
import org.kantega.reststop.cxf.api.CxfPluginPlugin;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.wsdl.Definition;
import javax.xml.ws.Endpoint;
import java.io.IOException;
import java.util.*;

/**
 *
 */
public class CxfReststopPlugin extends DefaultReststopPlugin {


    private final ReststopPluginManager pluginManager;
    private final EndpointConfigurationBuilder endpointConfigurationBuilder;
    private List<Endpoint> endpoints = new ArrayList<>();

    public static ThreadLocal<ClassLoader> pluginClassLoader = new ThreadLocal<>();

    @Config(defaultValue = "/ws/*")
    private String mountPoint;

    public CxfReststopPlugin(Reststop reststop,
                             final ReststopPluginManager pluginManager) throws ServletException {
        this.pluginManager = pluginManager;

        CXFNonSpringServlet cxfNonSpringServlet = new CXFNonSpringServlet();
        cxfNonSpringServlet.init(reststop.createServletConfig("cxf", new Properties()));


        addServletFilter(reststop.createServletFilter(cxfNonSpringServlet, mountPoint));

        addPluginListener(new PluginListener() {
            @Override
            public void pluginsUpdated(Collection<ReststopPlugin> plugins) {
                deployEndpoints();
            }

            @Override
            public void pluginManagerStarted() {
                deployEndpoints();
            }
        });

        endpointConfigurationBuilder = new DefaultEndpointConfigurationBuilder();
    }

    @Export
    public EndpointConfigurationBuilder getEndpointConfigurationBuilder() {
        return endpointConfigurationBuilder;
    }

    private void deployEndpoints() {
        for (Endpoint endpoint : endpoints) {
            endpoint.stop();
        }

        WSDLManager wsdlManager = WSDLManagerDefinitionCacheCleaner.getWsdlManager();
        for (Definition def : wsdlManager.getDefinitions().values()) {
            wsdlManager.removeDefinition(def);
        }
        for (JaxWsPlugin plugin : pluginManager.getPlugins(JaxWsPlugin.class)) {

            try {
                pluginClassLoader.set(pluginManager.getClassLoader(plugin));

                for (EndpointConfiguration config : plugin.getEndpointConfigurations()) {
                    Endpoint endpoint = Endpoint.create(config.getImplementor());
                    endpoint.publish(config.getPath());
                    for (CxfPluginPlugin cxfPluginPlugin : pluginManager.getPlugins(CxfPluginPlugin.class)) {
                        cxfPluginPlugin.customizeEndpoint(endpoint);
                    }
                    CxfReststopPlugin.this.endpoints.add(endpoint);
                }
            } finally {
                pluginClassLoader.remove();
            }
        }

    }

    private class DefaultEndpointConfigurationBuilder implements EndpointConfigurationBuilder {

        @Override
        public Build service(Object service) {
            return new DefaultBuild(service);
        }
        private class DefaultBuild implements Build {
            private final Object service;
            private String path;

            public DefaultBuild(Object service) {
                this.service = service;
            }

            @Override
            public Build path(String path) {
                this.path = path;
                return this;
            }

            @Override
            public EndpointConfiguration build() {
                return new EndpointConfiguration() {
                    @Override
                    public Object getImplementor() {
                        return service;
                    }

                    @Override
                    public String getPath() {
                        return path;
                    }
                };
            }

        }
    }
}
