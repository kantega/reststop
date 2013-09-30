package org.kantega.reststop.cxf;

import org.apache.cxf.transport.servlet.CXFNonSpringServlet;
import org.apache.cxf.wsdl.WSDLManager;
import org.kantega.reststop.api.*;
import org.kantega.reststop.jaxwsapi.EndpointConfiguration;
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
    private List<Endpoint> endpoints = new ArrayList<>();

    public static ThreadLocal<ClassLoader> pluginClassLoader = new ThreadLocal<>();

    public CxfReststopPlugin(Reststop reststop, final ReststopPluginManager pluginManager, ServletContext servletContext) {
        this.pluginManager = pluginManager;

        CXFNonSpringServlet cxfNonSpringServlet = new CXFNonSpringServlet();
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

            cxfNonSpringServlet.init(reststop.createServletConfig("cxf", new Properties()));
        } catch (ServletException e) {
            throw new RuntimeException("Failed starting CXF", e);
        } finally {
            Thread.currentThread().setContextClassLoader(loader);
        }
        CXFFilter cxfFilter = new CXFFilter(cxfNonSpringServlet);

        addServletFilter(reststop.createFilter(cxfFilter, "/ws/*", FilterPhase.USER));

        addPluginListener(new PluginListener() {
            @Override
            public void pluginsUpdated(Collection<ReststopPlugin> plugins) {
                deployEndpoints();
            }
        });
    }

    private void deployEndpoints() {
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        try {
            Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
            for (Endpoint endpoint : endpoints) {
                endpoint.stop();
            }

            WSDLManager wsdlManager = WSDLManagerDefinitionCacheCleaner.getWsdlManager();
            for(Definition def : wsdlManager.getDefinitions().values()) {
                wsdlManager.removeDefinition(def);
            }
            for(JaxWsPlugin plugin : pluginManager.getPlugins(JaxWsPlugin.class)) {

                try {
                    pluginClassLoader.set(pluginManager.getClassLoader(plugin));

                    for(EndpointConfiguration config : plugin.getEndpointConfigurations()) {
                        Endpoint endpoint = Endpoint.create(config.getImplementor());
                        endpoint.publish(config.getPath());
                        for(CxfPluginPlugin cxfPluginPlugin : pluginManager.getPlugins(CxfPluginPlugin.class)) {
                            cxfPluginPlugin.customizeEndpoint(endpoint);
                        }
                        CxfReststopPlugin.this.endpoints.add(endpoint);
                    }
                }finally {
                    pluginClassLoader.remove();
                }
                }
        } finally {
            Thread.currentThread().setContextClassLoader(loader);
        }
    }

    class CXFFilter implements Filter {


        private final CXFNonSpringServlet cxfNonSpringServlet;

        public CXFFilter(CXFNonSpringServlet cxfNonSpringServlet) {

            this.cxfNonSpringServlet = cxfNonSpringServlet;
        }

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {

        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
            HttpServletRequest req = (HttpServletRequest) servletRequest;
            HttpServletResponse resp = (HttpServletResponse) servletResponse;

            ClassLoader loader = Thread.currentThread().getContextClassLoader();

            try {
                Thread.currentThread().setContextClassLoader(getClass().getClassLoader());

                cxfNonSpringServlet.service(new HttpServletRequestWrapper(req) {
                    @Override
                    public String getServletPath() {
                        return "/ws";
                    }

                    @Override
                    public String getPathInfo() {
                        String requestURI = getRequestURI();
                        return requestURI.substring("/ws".length());
                    }
                }, resp);
            } finally {
                Thread.currentThread().setContextClassLoader(loader);
            }
        }

        @Override
        public void destroy() {

        }
    }

}
