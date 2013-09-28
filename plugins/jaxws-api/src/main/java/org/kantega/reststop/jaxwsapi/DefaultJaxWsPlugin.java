package org.kantega.reststop.jaxwsapi;

import org.kantega.reststop.api.DefaultReststopPlugin;


import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 *
 */
public class DefaultJaxWsPlugin extends DefaultReststopPlugin implements JaxWsPlugin {

    private final List<EndpointConfiguration> endpointConfigurations = new ArrayList<>();

    @Override
    public Collection<EndpointConfiguration> getEndpointConfigurations() {
        return endpointConfigurations;
    }

    public void addEndpointConfiguration(EndpointConfiguration endpointConfiguration) {
        this.endpointConfigurations.add(endpointConfiguration);
    }
}
