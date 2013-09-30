package org.kantega.reststop.metrics;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.health.jvm.ThreadDeadlockHealthCheck;
import com.codahale.metrics.jvm.*;
import com.codahale.metrics.servlets.HealthCheckServlet;
import com.codahale.metrics.servlets.MetricsServlet;
import org.kantega.reststop.api.DefaultReststopPlugin;
import org.kantega.reststop.api.FilterPhase;
import org.kantega.reststop.api.Reststop;

import javax.servlet.*;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Enumeration;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class MetricsReststopPlugin extends DefaultReststopPlugin {

    private final MetricRegistry metricRegistry;
    private final HealthCheckRegistry healthCheckRegistry;

    public MetricsReststopPlugin(Reststop reststop, ServletContext servletContext) throws ServletException {

        metricRegistry = initMetricsRegistry();
        MetricsServlet metricsServlet = new MetricsServlet(metricRegistry);
        metricsServlet.init(new EmptyServletConfig(createProxy(servletContext)));

        addServletFilter(reststop.createFilter(
                new ServletWrapper(metricsServlet),
                "/metrics/*",
                FilterPhase.USER));


        healthCheckRegistry = initHealthCheckRegistry();
        HealthCheckServlet healthCheckServlet = new HealthCheckServlet(healthCheckRegistry);
        healthCheckServlet.init(new EmptyServletConfig(servletContext));

        addServletFilter(reststop.createFilter(
                new ServletWrapper(healthCheckServlet),
                "/healthchecks/*",
                FilterPhase.USER
        ));

    }

    private ServletContext createProxy(final ServletContext servletContext) {
        return (ServletContext) Proxy.newProxyInstance(getClass().getClassLoader(), new Class[]{ServletContext.class}, new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getName().equals("getInitParameter")) {
                    if (MetricsServlet.DURATION_UNIT.equals(args[0])) {
                        return TimeUnit.MILLISECONDS.toString();
                    }
                }
                return method.invoke(servletContext, args);
            }
        });
    }

    private HealthCheckRegistry initHealthCheckRegistry() {
        HealthCheckRegistry registry = new HealthCheckRegistry();
        registry.register("threadDeadlock", new ThreadDeadlockHealthCheck());
        return registry;
    }

    private MetricRegistry initMetricsRegistry() {
        MetricRegistry registry = new MetricRegistry();

        registry.registerAll(new MemoryUsageGaugeSet());
        registry.register("fileDescriptorRation", new FileDescriptorRatioGauge());
        registry.registerAll(new GarbageCollectorMetricSet());
        registry.registerAll(new BufferPoolMetricSet(ManagementFactory.getPlatformMBeanServer()));
        registry.registerAll(new ThreadStatesGaugeSet());

        return registry;
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
