/*
 * Copyright 2013 Kantega AS
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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResolutionException;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.jetty.maven.plugin.JettyWebAppContext;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.List;

/**
 *
 */
@Mojo(name = "run",
        defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST,
requiresDependencyResolution = ResolutionScope.COMPILE)
public class RunMojo extends AbstractMojo {


    @Component
    private RepositorySystem repoSystem;

    @Parameter(defaultValue ="${repositorySystemSession}" ,readonly = true)
    private RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}")
    private List<RemoteRepository> remoteRepos;

    @Parameter (defaultValue = "${project.groupId}:reststop-webapp:war:${project.version}")
    private String warCoords;

    @Parameter (defaultValue = "${project.groupId}:reststop-development:jar:${project.version}")
    private String devCoords;

    @Parameter(defaultValue = "${project.build.directory}/reststop/temp")
    private File tempDirectory;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.${project.packaging}")
    private File pluginJar;

    @Parameter(defaultValue = "${wait}")
    private boolean wait;

    @Parameter(defaultValue = "${project}")
    private MavenProject mavenProject;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Start Reststop here!");

        File war = resolveArtifactFile(warCoords);

        startJetty(war);


    }

    private void startJetty(File war) throws MojoExecutionException {
        try {

            System.setProperty("reststopPluginDir", mavenProject.getBasedir().getAbsolutePath());

            int port = nextAvailablePort(8080);

            mavenProject.getProperties().setProperty("reststopPort", Integer.toString(port));

            Server server = new Server(port);

            JettyWebAppContext context = new JettyWebAppContext();
            context.setExtraClasspath(resolveArtifactFile(devCoords).getAbsolutePath());
            context.setWar(war.getAbsolutePath());
            context.setInitParameter("compileClasspath", getClasspath(mavenProject.getCompileArtifacts()));
            context.setInitParameter("runtimeClasspath", getClasspath(mavenProject.getRuntimeArtifacts()));

            tempDirectory.mkdirs();
            context.setTempDirectory(tempDirectory);
            context.setThrowUnavailableOnStartupException(true);

            HandlerCollection handlers = new HandlerCollection();

            handlers.addHandler(new ShutdownHandler(server, getLog()));
            handlers.addHandler(context);
            server.setHandler(handlers);

            server.start();

            if(wait) {
                server.join();
            }
        } catch (Exception e) {
            throw new MojoExecutionException("Failed starting Jetty ", e);
        }
    }


    private String getClasspath(List<org.apache.maven.artifact.Artifact> artifacts) {

        StringBuilder classpath = new StringBuilder();

        int c = 0;


        for (org.apache.maven.artifact.Artifact a : artifacts) {

            if (c > 0) {
                classpath.append(":");
            }
            c++;
            classpath.append(a.getFile().getAbsolutePath());


        }

        return classpath.toString();
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

    private File resolveArtifactFile(String coords) throws MojoFailureException, MojoExecutionException {
        Artifact artifact;
        try
        {
            artifact = new DefaultArtifact(coords);
        }
        catch ( IllegalArgumentException e )
        {
            throw new MojoFailureException( e.getMessage(), e );
        }

        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact( artifact );
        request.setRepositories( remoteRepos );

        getLog().info( "Resolving artifact " + artifact + " from " + remoteRepos );

        ArtifactResult result;
        try
        {
            result = repoSystem.resolveArtifact( repoSession, request );
        }
        catch ( ArtifactResolutionException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }

        getLog().info( "Resolved artifact " + artifact + " to " + result.getArtifact().getFile() + " from "
                + result.getRepository() );

        return result.getArtifact().getFile();
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

            if("/shutdown".equals(target)) {
                try {
                    log.info("Shutting down Jetty server");
                    new Thread() {
                        @Override
                        public void run() {
                            try {
                                Thread.sleep(500);
                                server.stop();
                            } catch (Exception e) {
                                throw new RuntimeException(e);
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
