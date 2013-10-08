package org.kantega.reststop.jerseymetrics;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerResponseContext;
import javax.ws.rs.container.ContainerResponseFilter;
import javax.ws.rs.core.Response;
import java.io.IOException;

import static com.codahale.metrics.MetricRegistry.name;

/**
 *
 */
public class TimerAfterFilter implements ContainerResponseFilter{
    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        Timer.Context context = (Timer.Context) requestContext.getProperty("metrics.timeContext");
        if(context != null) {
            context.stop();
        }
        if(responseContext.getStatus() != Response.Status.OK.getStatusCode()) {
            MetricRegistry registry = JerseyMetricsPlugin.getMetricRegistry();
            String path = (String) requestContext.getProperty("metrics.path");
            String name = name("REST","STATUS", Integer.toString(responseContext.getStatus()), requestContext.getMethod(), path);
            registry.meter(name).mark();

        }
    }
}
