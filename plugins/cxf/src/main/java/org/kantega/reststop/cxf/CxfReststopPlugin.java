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
import org.kantega.reststop.cxflib.api.EndpointCustomizer;
import org.kantega.reststop.jaxwsapi.EndpointConfiguration;
import org.kantega.reststop.jaxwsapi.EndpointConfigurationBuilder;
import org.kantega.reststop.jaxwsapi.EndpointDeployer;

import javax.servlet.Filter;
import javax.servlet.ServletException;
import javax.wsdl.Definition;
import javax.xml.ws.Endpoint;
import java.util.*;

/**
 *
 */
@Plugin
public class CxfReststopPlugin implements EndpointDeployer {


    private final ReststopPluginManager pluginManager;

    private final Collection<EndpointCustomizer> customizers;
    @Export
    private final Filter cxfServlet;
    @Export
    private final EndpointConfigurationBuilder endpointConfigurationBuilder;
    @Export
    private final EndpointDeployer deployer = this;
    @Export
    private final PluginListener listener;
    private List<Endpoint> endpoints = new ArrayList<>();

    public static ThreadLocal<ClassLoader> pluginClassLoader = new ThreadLocal<>();



    public CxfReststopPlugin(@Config(defaultValue = "/ws/*") String mountPoint,
                             Reststop reststop,
                             final ReststopPluginManager pluginManager,
                             Collection<EndpointCustomizer> endpointCustomizers) throws ServletException {
        this.pluginManager = pluginManager;
        this.customizers = endpointCustomizers;

        CXFNonSpringServlet cxfNonSpringServlet = new CXFNonSpringServlet();
        cxfNonSpringServlet.init(reststop.createServletConfig("cxf", new Properties()));

        listener = new PluginListener() {
            @Override
            public void pluginManagerStarted() {
                System.out.println("Would have deployed with customizers: " + pluginManager.findExports(EndpointCustomizer.class).size());
            }
        };

        cxfServlet = reststop.createServletFilter(cxfNonSpringServlet, mountPoint);

        endpointConfigurationBuilder = new DefaultEndpointConfigurationBuilder();
    }


    private void deployEndpoints(Collection<EndpointCustomizer> customizers, Collection<EndpointConfiguration> endpoints) {
        for (Endpoint endpoint : this.endpoints) {
            endpoint.stop();
        }

        WSDLManager wsdlManager = WSDLManagerDefinitionCacheCleaner.getWsdlManager();
        for (Definition def : wsdlManager.getDefinitions().values()) {
            wsdlManager.removeDefinition(def);
        }
        for (EndpointConfiguration config : endpoints) {

            try {
                pluginClassLoader.set(config.getClassLoader());
                Endpoint endpoint = Endpoint.create(config.getImplementor());
                endpoint.publish(config.getPath());
                for (EndpointCustomizer cxfPluginPlugin : customizers) {
                    cxfPluginPlugin.customizeEndpoint(endpoint);
                }
                CxfReststopPlugin.this.endpoints.add(endpoint);

            } finally {
                pluginClassLoader.remove();
            }
        }

    }

    @Override
    public void deploy(Collection<EndpointConfiguration> endpoints) {
        deployEndpoints(customizers, endpoints);
    }

    private class DefaultEndpointConfigurationBuilder implements EndpointConfigurationBuilder {

        @Override
        public Build service(Class clazz, Object service) {
            return new DefaultBuild(clazz.getClassLoader(), service);
        }
        private class DefaultBuild implements Build {
            private final ClassLoader classLoader;
            private final Object service;
            private String path;

            public DefaultBuild(ClassLoader classLoader, Object service) {
                this.classLoader = classLoader;
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
                    public ClassLoader getClassLoader() {
                        return classLoader;
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
