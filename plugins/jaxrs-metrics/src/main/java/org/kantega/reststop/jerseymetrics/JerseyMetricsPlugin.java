package org.kantega.reststop.jerseymetrics;

import com.codahale.metrics.MetricRegistry;
import org.kantega.reststop.api.ReststopPluginManager;
import org.kantega.reststop.jaxrsapi.DefaultJaxRsPlugin;
import org.kantega.reststop.metrics.MetricsReststopPlugin;

/**
 *
 */
public class JerseyMetricsPlugin extends DefaultJaxRsPlugin{

    private static ReststopPluginManager pluginManager;

    public JerseyMetricsPlugin(ReststopPluginManager pluginManager) {
        JerseyMetricsPlugin.pluginManager = pluginManager;
        addJaxRsContainerClass(TimerBeforeFilter.class);
        addJaxRsContainerClass(TimerAfterFilter.class);

    }

    public static MetricRegistry getMetricRegistry() {
        return pluginManager.getPlugins(MetricsReststopPlugin.class).iterator().next().getMetricRegistry();
    }



}
