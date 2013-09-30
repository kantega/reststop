package org.kantega.reststop.jerseymetrics;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

import javax.ws.rs.container.ContainerRequestContext;
import javax.ws.rs.container.ContainerRequestFilter;
import javax.ws.rs.ext.Provider;
import java.io.IOException;

import static com.codahale.metrics.MetricRegistry.name;

/**
 *
 */
@Provider
public class TimerBeforeFilter implements ContainerRequestFilter {



    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {

        MetricRegistry registry = JerseyMetricsPlugin.getMetricRegistry();

        String name = name("REST", requestContext.getUriInfo().getPath(),
                            requestContext.getMethod());

        Timer.Context context = registry.timer(name).time();

        requestContext.setProperty("timeContext", context);

    }


}
