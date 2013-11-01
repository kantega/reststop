package org.kantega.reststop.metrics;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.codahale.metrics.health.jvm.ThreadDeadlockHealthCheck;
import com.codahale.metrics.jvm.*;
import com.codahale.metrics.servlets.HealthCheckServlet;
import com.codahale.metrics.servlets.MetricsServlet;
import org.kantega.reststop.api.DefaultReststopPlugin;
import org.kantega.reststop.api.Export;
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
import java.util.Properties;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class MetricsReststopPlugin extends DefaultReststopPlugin {


    private final HealthCheckRegistry healthCheckRegistry;
    private final MetricRegistry metricRegistry;

    public MetricsReststopPlugin(Reststop reststop, ServletContext servletContext) throws ServletException {

        metricRegistry = initMetricsRegistry();
        MetricsServlet metricsServlet = new MetricsServlet(metricRegistry);
        metricsServlet.init(new EmptyServletConfig(createProxy(servletContext)));

        addServletFilter(reststop.createServletFilter(
                metricsServlet,
                "/metrics/*"));


        healthCheckRegistry = initHealthCheckRegistry();
        HealthCheckServlet healthCheckServlet = new HealthCheckServlet(healthCheckRegistry);
        healthCheckServlet.init(reststop.createServletConfig("healthcheck", new Properties()));

        addServletFilter(reststop.createServletFilter(
                healthCheckServlet,
                "/healthchecks/*"
        ));

    }

    @Export
    public MetricRegistry getMetricRegistry() {
        return metricRegistry;
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
