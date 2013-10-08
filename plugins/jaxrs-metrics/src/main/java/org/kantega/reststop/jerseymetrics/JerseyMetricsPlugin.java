package org.kantega.reststop.jerseymetrics;

import com.codahale.metrics.MetricRegistry;
import org.kantega.reststop.jaxrsapi.DefaultJaxRsPlugin;

/**
 *
 */
public class JerseyMetricsPlugin extends DefaultJaxRsPlugin{


    public static MetricRegistry metricRegistry;

    public JerseyMetricsPlugin(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
        addJaxRsContainerClass(TimerFeature.class);
        addJaxRsContainerClass(AroundWriteMeter.class);
    }

    public static MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }
}
