package org.kantega.reststop.helloworld;

import org.eclipse.jetty.websocket.jsr356.ClientContainer;
import org.eclipse.jetty.websocket.jsr356.decoders.StringDecoder;
import org.eclipse.jetty.websocket.jsr356.encoders.StringEncoder;
import org.junit.jupiter.api.Test;

import javax.websocket.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.kantega.reststop.helloworld.Utils.readPort;

public class WebSocketIT {

    @Test
    void testToUpper() throws Exception {
        Set<String> messages = new HashSet<>(asList("REst", "sTop", "ReststOp"));
        Set<String> received = new HashSet<>();

        CountDownLatch latch = new CountDownLatch(messages.size());

        ClientContainer clientContainer = new ClientContainer();

        ClientEndpointConfig config = ClientEndpointConfig.Builder.create()
                .configurator(new ClientEndpointConfig.Configurator() {
                    @Override
                    public void beforeRequest(Map<String, List<String>> headers) {
                        super.beforeRequest(headers);
                        headers.put("Authorization", asList("Basic am9lOmpvZQ=="));
                    }
                })
                .decoders(asList(StringDecoder.class))
                .encoders(asList(StringEncoder.class))
                .build();
        clientContainer.start();
        Session session = clientContainer.connectToServer(new Endpoint() {
            @Override
            public void onOpen(Session session, EndpointConfig config) {
                session.addMessageHandler(new MessageHandler.Whole<String>() {
                    @Override
                    public void onMessage(String message) {
                            received.add(message);
                            latch.countDown();
                    }
                });
            }
        }, config, getEndpointURI());

        RemoteEndpoint.Basic basicRemote = session.getBasicRemote();
        for (String message : messages) {
            basicRemote.sendText(message);
        }
        latch.await();
        assertEquals(
                messages.stream().map(String::toUpperCase).collect(Collectors.toSet()),
                received);
    }

    private URI getEndpointURI() throws URISyntaxException {
        String reststopPort = readPort();
        return new URI("ws://localhost:" + reststopPort + "/toupper");
    }
}
