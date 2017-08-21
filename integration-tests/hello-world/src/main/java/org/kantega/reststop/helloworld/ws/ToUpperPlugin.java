package org.kantega.reststop.helloworld.ws;

import org.kantega.reststop.api.Export;
import org.kantega.reststop.api.Plugin;
import org.kantega.reststop.servlet.api.ServletBuilder;

import javax.servlet.Filter;
import javax.servlet.ServletContext;

import javax.websocket.DeploymentException;
import javax.websocket.server.ServerContainer;

/**
 *
 */
@Plugin
public class ToUpperPlugin {

    @Export
    private final Filter ws;

    public ToUpperPlugin(ServletContext context, ServletBuilder builder) throws DeploymentException {
        ServerContainer cont = (ServerContainer) context.getAttribute(ServerContainer.class.getName());
        cont.addEndpoint(ToUpperEndpoint.class);

        ws = builder.resourceServlet(getClass().getResource("/assets/ws/upper.html"), "/upper.html");
    }
}
