/*
 * Copyright 2015 Kantega AS
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

package org.kantega.reststop.jetty.websockets;

import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import org.kantega.reststop.api.Export;
import org.kantega.reststop.api.Plugin;
import org.kantega.reststop.jetty.ServletContextCustomizer;

import javax.servlet.ServletException;

/**
 *
 */
@Plugin
public class JettyWebsocketsPlugin {

    @Export final ServletContextCustomizer servletContextCustomizer;

    public JettyWebsocketsPlugin()throws Exception {

        servletContextCustomizer = new ServletContextCustomizer() {
            @Override
            public void customize(ServletContextHandler contextHandler) throws ServletException {
                Thread thread = Thread.currentThread();

                ClassLoader oldCl = thread.getContextClassLoader();

                try {
                    thread.setContextClassLoader(JettyWebsocketsPlugin.class.getClassLoader());
                    WebSocketServerContainerInitializer.configureContext(contextHandler);
                } finally {
                    thread.setContextClassLoader(oldCl);
                }
            }
        };

    }
}
