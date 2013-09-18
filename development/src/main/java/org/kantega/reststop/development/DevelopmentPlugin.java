package org.kantega.reststop.development;

import org.kantega.reststop.api.DefaultReststopPlugin;
import org.kantega.reststop.api.PluginListener;
import org.kantega.reststop.api.Reststop;

/**
 *
 */
public class DevelopmentPlugin extends DefaultReststopPlugin {
    public DevelopmentPlugin(final Reststop reststop) {
        final DevelopmentClassLoaderProvider provider = new DevelopmentClassLoaderProvider();
        addPluginListener(new PluginListener() {
            @Override
            public void pluginManagerStarted() {
                provider.start(reststop);
            }
        });

        addServletFilter(new RedeployFilter(provider));

    }
}
