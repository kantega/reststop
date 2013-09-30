package org.kantega.reststop.helloworld.jaxws;

import org.kantega.reststop.helloworld.jaxws.HelloService;
import org.kantega.reststop.jaxwsapi.DefaultJaxWsPlugin;
import org.kantega.reststop.jaxwsapi.EndpointConfiguration;

/**
 *
 */
public class HelloworldWsPlugin extends DefaultJaxWsPlugin {

    public HelloworldWsPlugin() {
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
