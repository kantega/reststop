package org.kantega.reststop.helloworld.jaxws;

import org.kantega.reststop.helloworld.jaxws.HelloService;
import org.kantega.reststop.jaxwsapi.DefaultJaxWsPlugin;
import org.kantega.reststop.jaxwsapi.EndpointConfiguration;
import org.kantega.reststop.jaxwsapi.EndpointConfigurationBuilder;

/**
 *
 */
public class HelloworldWsPlugin extends DefaultJaxWsPlugin {

    public HelloworldWsPlugin(EndpointConfigurationBuilder builder) {
        addEndpointConfiguration(builder.service(new HelloService()).path("/hello-1.0"));
    }
}
