package org.kantega.reststop.helloworld.ws;

import org.kantega.reststop.api.Plugin;

import javax.servlet.ServletContext;

import javax.websocket.DeploymentException;
import javax.websocket.server.ServerContainer;

/**
 *
 */
@Plugin
public class ToUpperPlugin {

    public ToUpperPlugin(ServletContext context) throws DeploymentException {
        ServerContainer cont = (ServerContainer) context.getAttribute(ServerContainer.class.getName());
        cont.addEndpoint(ToUpperEndpoint.class);
    }
}
