package org.kantega.reststop.metrics;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.servlets.HealthCheckServlet;
import com.codahale.metrics.servlets.MetricsServlet;
import org.kantega.reststop.api.DefaultReststopPlugin;
import org.kantega.reststop.api.FilterPhase;
import org.kantega.reststop.api.Reststop;

import javax.servlet.*;
import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;

/**
 *
 */
public class MetricsReststopPlugin extends DefaultReststopPlugin {

    private final MetricRegistry metricRegistry;
    private final HealthCheckRegistry healthCheckRegistry;

    public MetricsReststopPlugin(Reststop reststop, ServletContext servletContext) throws ServletException {

        metricRegistry = new MetricRegistry();
        MetricsServlet metricsServlet = new MetricsServlet(metricRegistry);
        metricsServlet.init(new EmptyServletConfig(servletContext));

        addServletFilter(reststop.createFilter(
                new ServletWrapper(metricsServlet),
                "/metrics/*",
                FilterPhase.USER));


        healthCheckRegistry = new HealthCheckRegistry();
        HealthCheckServlet healthCheckServlet = new HealthCheckServlet(healthCheckRegistry);
        healthCheckServlet.init(new EmptyServletConfig(servletContext));

        addServletFilter(reststop.createFilter(
                new ServletWrapper(healthCheckServlet),
                "/healthchecks/*",
                FilterPhase.USER
        ));

    }

    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }

    private class ServletWrapper implements Filter {
        private final Servlet metricsServlet;

        public ServletWrapper(Servlet metricsServlet) {
            this.metricsServlet = metricsServlet;
        }

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {

        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
            metricsServlet.service(servletRequest, servletResponse);
        }

        @Override
        public void destroy() {

        }
    }

    private class EmptyServletConfig implements ServletConfig {
        private final ServletContext servletContext;

        private EmptyServletConfig(ServletContext servletContext) {
            this.servletContext = servletContext;
        }

        @Override
        public String getServletName() {
            return "metrics";
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
            return Collections.emptyEnumeration();
        }
    }
}
