package org.kantega.reststop.helloworld.ws;

import javax.websocket.OnMessage;
import javax.websocket.server.ServerEndpoint;

/**
 *
 */
@ServerEndpoint(value = "/toupper", configurator = CustomWsConfiguator.class)
public class ToUpperEndpoint {

    @OnMessage
    public String onMessage(String message) {
        return message.toUpperCase();
    }
}
