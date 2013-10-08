package org.kantega.reststop.jerseymetrics;

import com.codahale.metrics.MetricRegistry;

import static com.codahale.metrics.MetricRegistry.name;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.ext.Provider;
import javax.ws.rs.ext.WriterInterceptor;
import javax.ws.rs.ext.WriterInterceptorContext;
import java.io.IOException;

/**
 *
 */
@Provider
public class AroundWriteMeter implements WriterInterceptor {
    @Override
    public void aroundWriteTo(WriterInterceptorContext context) throws IOException, WebApplicationException {
        try {
            context.proceed();
        } catch (Throwable e) {
            String path = (String) context.getProperty("metrics.path");
            String name = name("REST", "WRITE", e.getClass().getSimpleName(), path);
            JerseyMetricsPlugin.getMetricRegistry().meter(name).mark();

            throw e;
        }
    }
}
