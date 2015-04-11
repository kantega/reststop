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

package org.kantega.reststop.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.jetty.maven.plugin.JettyWebAppContext;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;

/**
 *
 */
public abstract class AbstractReststopRunMojo extends AbstractReststopMojo {

    @Parameter(required = true)
    private String applicationName;

    @Parameter (defaultValue = "/")
    private String contextPath;

    @Parameter(defaultValue =  "${basedir}/src/config")
    private File configDir;

    @Parameter(defaultValue = "${project.build.directory}/reststop/temp")
    private File tempDirectory;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        File war = resolveArtifactFile(warCoords);

        startJetty(war);


    }

    private void startJetty(File war) throws MojoExecutionException {
        try {

            System.setProperty("reststopPluginDir", mavenProject.getBasedir().getAbsolutePath());

            int port = nextAvailablePort(8080);

            mavenProject.getProperties().setProperty("reststopPort", Integer.toString(port));
            System.setProperty("reststopPort", Integer.toString(port));

            Server server = new Server(port);

            mavenProject.setContextValue("jettyServer", server);

            JettyWebAppContext context = new JettyWebAppContext();

            context.addServerClass("org.eclipse.aether.");
            context.setWar(war.getAbsolutePath());
            context.setContextPath(contextPath);
            context.getServletContext().setAttribute("pluginsXml", createPluginXmlDocument(false));
            context.setInitParameter("pluginConfigurationDirectory", configDir.getAbsolutePath());
            context.setInitParameter("applicationName", applicationName);

            customizeContext(context);

            tempDirectory.mkdirs();
            context.setTempDirectory(tempDirectory);
            context.setThrowUnavailableOnStartupException(true);

            HandlerCollection handlers = new HandlerCollection();

            handlers.addHandler(new ShutdownHandler(server, getLog()));
            handlers.addHandler(context);
            server.setHandler(handlers);

            server.start();

            afterServerStart(server, port);

        } catch (Exception e) {
            throw new MojoExecutionException("Failed starting Jetty ", e);
        }
    }

    private int nextAvailablePort(int first) {
        int port = first;
        for(;;) {
            try {
                ServerSocket socket = new ServerSocket(port);
                socket.close();
                return port;
            } catch (IOException e) {
                port++;
            }
        }
    }

    private class ShutdownHandler extends AbstractHandler {
        private final Server server;
        private final Log log;

        public ShutdownHandler(Server server, Log log) {
            this.server = server;
            this.log = log;
        }

        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {

            if("/shutdown".equals(target) && ! (server.isStopping() || server.isStopped())) {
                try {
                    log.info("Shutting down Jetty server");
                    new Thread() {
                        @Override
                        public void run() {
                            try {
                                server.stop();
                            } catch (Throwable e) {
                                org.eclipse.jetty.util.log.Log.getLogger(getClass()).ignore(e);
                            }
                        }
                    }.start();
                } catch (Exception e) {
                    throw new ServletException(e);
                }
                baseRequest.setHandled(true);
            }
        }
    }
}
