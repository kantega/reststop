package org.kantega.reststop.jaxwsapi;

import org.kantega.reststop.api.ReststopPlugin;

import java.util.Collection;

/**
 *
 */
public interface JaxWsPlugin extends ReststopPlugin {

    Collection<EndpointConfiguration> getEndpointConfigurations();
}
