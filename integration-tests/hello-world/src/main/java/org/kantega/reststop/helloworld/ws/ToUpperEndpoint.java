package org.kantega.reststop.helloworld.ws;

import javax.websocket.OnMessage;
import javax.websocket.server.ServerEndpoint;

/**
 *
 */
@ServerEndpoint("/toupper")
public class ToUpperEndpoint {

    @OnMessage
    public String onMessage(String message) {
        return message.toLowerCase();
    }
}
