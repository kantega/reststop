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

package org.kantega.reststop.cxf;

import org.apache.cxf.transport.servlet.CXFNonSpringServlet;
import org.apache.cxf.wsdl.WSDLManager;
import org.kantega.reststop.api.*;
import org.kantega.reststop.jaxwsapi.EndpointConfiguration;
import org.kantega.reststop.jaxwsapi.EndpointConfigurationBuilder;
import org.kantega.reststop.jaxwsapi.EndpointDeployer;

import javax.annotation.PreDestroy;
import javax.servlet.Filter;
import javax.servlet.ServletException;
import javax.wsdl.Definition;
import javax.xml.ws.Endpoint;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Properties;

/**
 *
 */
@Plugin
public class CxfPlugin implements EndpointDeployer {


    private final Collection<EndpointCustomizer> customizers;
    @Export
    private final Filter cxfServlet;
    @Export
    private final EndpointConfigurationBuilder endpointConfigurationBuilder;
    @Export
    private final EndpointDeployer deployer = this;

    private List<Endpoint> endpoints = new ArrayList<>();

    public static ThreadLocal<ClassLoader> pluginClassLoader = new ThreadLocal<>();



    public CxfPlugin(@Config(defaultValue = "/ws/*", doc = "The path to deploy the CXF servlet") String mountPoint,
                     ServletBuilder servletBuilder,
                     Collection<EndpointCustomizer> endpointCustomizers) throws ServletException {
        this.customizers = endpointCustomizers;

        CXFNonSpringServlet cxfNonSpringServlet = new CXFNonSpringServlet();
        cxfNonSpringServlet.init(servletBuilder.servletConfig("cxf", new Properties()));

        cxfServlet = servletBuilder.servlet(cxfNonSpringServlet, mountPoint);

        endpointConfigurationBuilder = new DefaultEndpointConfigurationBuilder();
    }


    private void deployEndpoints(Collection<EndpointCustomizer> customizers, Collection<PluginExport<EndpointConfiguration>> endpoints) {
        undeployEndpoints();

        WSDLManager wsdlManager = WSDLManagerDefinitionCacheCleaner.getWsdlManager();
        for (Definition def : wsdlManager.getDefinitions().values()) {
            wsdlManager.removeDefinition(def);
        }
        for (PluginExport<EndpointConfiguration> export : endpoints) {

            EndpointConfiguration config = export.getExport();
            try {
                pluginClassLoader.set(export.getClassLoader());
                Endpoint endpoint = Endpoint.create(config.getImplementor());
                endpoint.publish(config.getPath());
                for (EndpointCustomizer cxfPluginPlugin : customizers) {
                    cxfPluginPlugin.customizeEndpoint(endpoint);
                }
                CxfPlugin.this.endpoints.add(endpoint);

            } finally {
                pluginClassLoader.remove();
            }
        }

    }

    @PreDestroy
    public void destroy() {

        undeployEndpoints();
    }

    private void undeployEndpoints() {
        for (Endpoint endpoint : this.endpoints) {
            endpoint.stop();
        }
    }

    @Override
    public void deploy(Collection<PluginExport<EndpointConfiguration>> endpoints) {
        deployEndpoints(customizers, endpoints);
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
