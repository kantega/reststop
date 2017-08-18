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

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.Parameter;
import org.eclipse.jetty.maven.plugin.JettyWebAppContext;
import org.eclipse.jetty.maven.plugin.ServerSupport;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

import static java.util.Arrays.asList;

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


    @Parameter(defaultValue = "${project.build.testOutputDirectory}/reststopPort.txt")
    private File reststopPortFile;

    @Parameter(defaultValue = "8080")
    private int port;

    @Parameter(defaultValue = "${basedir}/src/main/webapp")
    protected File webAppSourceDirectory;

    @Parameter(defaultValue = "${project.build.outputDirectory}")
    protected File classesDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        File war;
        if("war".equals(mavenProject.getPackaging())) {
            war = webAppSourceDirectory;
        } else {
            war = resolveArtifactFile(warCoords);
        }


        startJetty(war);


    }

    private void startJetty(File war) throws MojoExecutionException {
        try {


            System.setProperty("reststopPluginDir", mavenProject.getBasedir().getAbsolutePath());

            int port = this.port;
            if(port != 0) {
                port = nextAvailablePort(port);
            }


            Server server = new Server(port);
            ServerSupport.configureDefaultConfigurationClasses(server);

            mavenProject.setContextValue("jettyServer", server);

            JettyWebAppContext context = new JettyWebAppContext();

            List<String> serverClasses = asList(
                    "org.eclipse.aether.",
                    "com.sun.codemodel.",
                    "com.jcraft.",
                    "org.apache.commons.",
                    "org.apache.ant.",
                    "org.apache.http.",
                    "org.apache.maven.",
                    "org.codehaus.plexus.",
                    "org.sonatype.plexus.",
                    "org.eclipse.jgit.",
                    "org.twdata.",
                    "com.googlecode.");
            serverClasses.forEach(context::addServerClass);
            getLog().info("Added system classes: " + serverClasses);

            context.prependServerClass("-org.eclipse.jetty.http.pathmap.");

            context.setWar(war.getAbsolutePath());
            context.setContextPath(contextPath);
            context.getServletContext().setAttribute("pluginsXml", createPluginXmlDocument(false));
            context.setInitParameter("pluginConfigurationDirectory", configDir.getAbsolutePath());
            context.setInitParameter("applicationName", applicationName);

            customizeContext(context);


            File jettyTmpDir = new File(tempDirectory, war.getName());
            jettyTmpDir.mkdirs();
            context.setTempDirectory(jettyTmpDir);
            boolean deleteTempDirectory= jettyTmpDir.exists() && war.lastModified() > jettyTmpDir.lastModified();
            context.setPersistTempDirectory(!deleteTempDirectory);
            context.setThrowUnavailableOnStartupException(true);

            HandlerCollection handlers = new HandlerCollection(true);

            handlers.addHandler(new ShutdownHandler(context, server, getLog()));
            server.setHandler(handlers);

            server.start();


            ServerConnector connector = (ServerConnector) server.getConnectors()[0];
            int actualPort = connector.getLocalPort();

            mavenProject.getProperties().setProperty("reststopPort", Integer.toString(actualPort));
            String reststopPort = Integer.toString(actualPort);
            System.setProperty("reststopPort", reststopPort);
            FileUtils.writeStringToFile(reststopPortFile, reststopPort);

            handlers.addHandler(context);

            WebSocketServerContainerInitializer.configureContext(context);

            context.start();

            afterServerStart(server, actualPort);

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
        private final JettyWebAppContext context;
        private final Server server;
        private final Log log;

        public ShutdownHandler(JettyWebAppContext context, Server server, Log log) {
            this.context = context;
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
                                context.stop();
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
