package org.kantega.reststop.maven;

import org.eclipse.jetty.http.pathmap.MappedResource;
import org.eclipse.jetty.websocket.jsr356.server.ServerContainer;
import org.eclipse.jetty.websocket.jsr356.server.ServerEndpointMetadata;
import org.eclipse.jetty.websocket.server.MappedWebSocketCreator;
import org.eclipse.jetty.websocket.server.WebSocketServerFactory;
import org.eclipse.jetty.websocket.servlet.WebSocketCreator;

import javax.websocket.DeploymentException;
import javax.websocket.server.ServerEndpointConfig;
import java.util.Iterator;
import java.util.concurrent.Executor;

/**
 *
 */
public class RedeployableServerContainer extends ServerContainer {
    private final MappedWebSocketCreator creator;

    public RedeployableServerContainer(MappedWebSocketCreator creator, WebSocketServerFactory factory, Executor executor) {
        super(creator, factory, executor);
        this.creator = creator;
    }

    @Override
    public void addEndpoint(Class<?> endpointClass) throws DeploymentException {
        if (isStarted() || isStarting()) {
            ServerEndpointMetadata metadata = getServerEndpointMetadata(endpointClass, null);
            removeMapping(metadata.getPath());
            super.addEndpoint(endpointClass);
        } else {
            super.addEndpoint(endpointClass);
        }

    }

    @Override
    public void addEndpoint(ServerEndpointConfig config) throws DeploymentException {
        if (isStarted() || isStarting()) {
            removeMapping(config.getPath());
            super.addEndpoint(config);
        } else {
            super.addEndpoint(config);
        }
    }

    private void removeMapping(String path) {
        Iterator<MappedResource<WebSocketCreator>> iterator = creator.getMappings().iterator();
        while (iterator.hasNext()) {
            MappedResource<WebSocketCreator> next = iterator.next();
            if(next.getPathSpec().getDeclaration().equals(path)) {
                iterator.remove();
            }
        }
    }
}
