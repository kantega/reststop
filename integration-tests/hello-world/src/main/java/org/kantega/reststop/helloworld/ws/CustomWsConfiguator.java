package org.kantega.reststop.helloworld.ws;

import javax.websocket.HandshakeResponse;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;

/**
 *
 */
public class CustomWsConfiguator extends ServerEndpointConfig.Configurator {

    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
        // nada
    }
}
