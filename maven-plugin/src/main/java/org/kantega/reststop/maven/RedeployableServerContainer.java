/*
 * Copyright 2018 Kantega AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kantega.reststop.maven;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.websocket.jsr356.server.ServerContainer;
import org.eclipse.jetty.websocket.jsr356.server.ServerEndpointMetadata;
import org.eclipse.jetty.websocket.server.MappedWebSocketCreator;
import org.eclipse.jetty.websocket.server.NativeWebSocketConfiguration;

import javax.websocket.DeploymentException;
import javax.websocket.server.ServerEndpointConfig;

/**
 *
 */
public class RedeployableServerContainer extends ServerContainer {
    private final MappedWebSocketCreator creator;

    public RedeployableServerContainer(MappedWebSocketCreator creator,
                                       NativeWebSocketConfiguration configuration,
                                       HttpClient httpClient) {
        super(configuration, httpClient);
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
        // Due to how removeMapping and ServerEndpointMetadata.getPath() works we need to try these different
        // mappings. See NativeWebSocketConfiguration.toPathSpec(path)
        creator.removeMapping(path);
        creator.removeMapping("uri-template|" + path);
        creator.removeMapping("regex|" + path);
    }
}
