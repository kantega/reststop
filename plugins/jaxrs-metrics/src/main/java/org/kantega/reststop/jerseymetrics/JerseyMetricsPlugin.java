package org.kantega.reststop.jerseymetrics;

import com.codahale.metrics.MetricRegistry;
import org.kantega.reststop.api.ReststopPluginManager;
import org.kantega.reststop.jaxrsapi.DefaultJaxRsPlugin;
import org.kantega.reststop.metrics.MetricsReststopPlugin;

/**
 *
 */
public class JerseyMetricsPlugin extends DefaultJaxRsPlugin{


    public static MetricRegistry metricRegistry;

    public JerseyMetricsPlugin(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
        addJaxRsContainerClass(TimerBeforeFilter.class);
        addJaxRsContainerClass(TimerAfterFilter.class);
    }

    public static MetricRegistry getMetricRegistry() {
        return metricRegistry;
    }
}
