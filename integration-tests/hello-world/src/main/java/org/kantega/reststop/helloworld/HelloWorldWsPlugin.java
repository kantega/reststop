package org.kantega.reststop.helloworld;

import org.kantega.reststop.jaxwsapi.DefaultJaxWsPlugin;
import org.kantega.reststop.jaxwsapi.EndpointConfiguration;

/**
 *
 */
public class HelloWorldWsPlugin extends DefaultJaxWsPlugin {

    public HelloWorldWsPlugin() {
        final HelloService service = new HelloService();
        addEndpointConfiguration(new EndpointConfiguration() {
            @Override
            public Object getImplementor() {
                return service;
            }

            @Override
            public String getPath() {
                return "/hello-1.0";
            }
        });
    }
}
