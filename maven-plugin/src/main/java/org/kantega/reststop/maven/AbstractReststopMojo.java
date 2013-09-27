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
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.jetty.maven.plugin.JettyWebAppContext;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.net.ServerSocket;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

import static java.util.Arrays.asList;

/**
 *
 */
public abstract class AbstractReststopMojo extends AbstractMojo {


    @Component
    private RepositorySystem repoSystem;

    @Parameter(defaultValue ="${repositorySystemSession}" ,readonly = true)
    protected RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}")
    private List<RemoteRepository> remoteRepos;

    @Parameter (defaultValue = "org.kantega.reststop:reststop-webapp:war:${plugin.version}")
    private String warCoords;

    @Parameter(defaultValue = "${project.build.directory}/reststop/temp")
    private File tempDirectory;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.${project.packaging}")
    private File pluginJar;

    @Parameter(defaultValue = "${project}")
    protected MavenProject mavenProject;

    @Parameter
    private List<Plugin> plugins;


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

            context.setWar(war.getAbsolutePath());
            context.getServletContext().setAttribute("pluginsXml", createPluginXmlDocument());

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

    protected void customizeContext(JettyWebAppContext context) {

    }

    protected void afterServerStart(Server server, int port) throws MojoFailureException {

    }

    protected Document createPluginXmlDocument() throws MojoFailureException, MojoExecutionException {
        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

            Element pluginsElem = doc.createElement("plugins");

            doc.appendChild(pluginsElem);

            for (Plugin plugin : getPlugins()) {

                Element pluginElem = doc.createElement("plugin");
                pluginsElem.appendChild(pluginElem);

                pluginElem.setAttribute("groupId", plugin.getGroupId());
                pluginElem.setAttribute("artifactId", plugin.getArtifactId());
                pluginElem.setAttribute("version", plugin.getVersion());
                pluginElem.setAttribute("directDeploy", Boolean.toString(plugin.isDirectDeploy()));
                if(plugin.getSourceDirectory() != null) {
                    pluginElem.setAttribute("sourceDirectory", plugin.getSourceDirectory().getAbsolutePath());
                }

                Artifact pluginArtifact = resolveArtifact(plugin.getCoords());

                pluginElem.setAttribute("pluginFile", pluginArtifact.getFile().getAbsolutePath());

                for(String scope : asList(JavaScopes.TEST, JavaScopes.RUNTIME, JavaScopes.COMPILE)) {

                    Element scopeElem = doc.createElement(scope);

                    pluginElem.appendChild(scopeElem);

                    try {

                        ArtifactDescriptorResult descriptorResult = repoSystem.readArtifactDescriptor(repoSession, new ArtifactDescriptorRequest(pluginArtifact, remoteRepos, null));

                        CollectRequest collectRequest = new CollectRequest();

                        for (RemoteRepository repo : remoteRepos) {
                            collectRequest.addRepository(repo);
                        }
                        for (Dependency dependency : descriptorResult.getDependencies()) {
                            collectRequest.addDependency(dependency);
                        }

                        DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, DependencyFilterUtils.classpathFilter(scope));

                        DependencyResult dependencyResult = repoSystem.resolveDependencies(repoSession, dependencyRequest);

                        if(!dependencyResult.getCollectExceptions().isEmpty()) {
                            throw new MojoFailureException("Failed resolving plugin dependencies", dependencyResult.getCollectExceptions().get(0));
                        }

                        for(ArtifactResult result : dependencyResult.getArtifactResults()) {
                            Artifact artifact = result.getArtifact();
                            Element artifactElement = doc.createElement("artifact");
                            artifactElement.setAttribute("groupId", artifact.getGroupId());
                            artifactElement.setAttribute("artifactId", artifact.getArtifactId());
                            artifactElement.setAttribute("version", artifact.getVersion());

                            artifactElement.setAttribute("file", artifact.getFile().getAbsolutePath());

                            scopeElem.appendChild(artifactElement);
                        }

                    } catch (DependencyResolutionException | ArtifactDescriptorException e) {
                        throw new MojoFailureException("Failed resolving plugin dependencies", e);
                    }


                }
            }
            return doc;
        } catch (ParserConfigurationException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }


    }

    protected List<Plugin> getPlugins() {
        List<Plugin> plugins = new ArrayList<>();

        if(this.plugins != null) {
            plugins.addAll(this.plugins);
        }


        return plugins;
    }


    private String getClasspath(List<org.apache.maven.artifact.Artifact> artifacts) {

        StringBuilder classpath = new StringBuilder();

        int c = 0;


        for (org.apache.maven.artifact.Artifact a : artifacts) {

            if (c > 0) {
                classpath.append(File.pathSeparatorChar);
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
        return resolveArtifact(coords).getFile();
    }


    private Artifact resolveArtifact(String coords) throws MojoFailureException, MojoExecutionException {
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

        return result.getArtifact();
    }

    protected File getSourceDirectory(Plugin plugin) {
        String path = repoSession.getLocalRepositoryManager().getPathForLocalArtifact(new DefaultArtifact(plugin.getGroupId(), plugin.getArtifactId(), "sourceDir", plugin.getVersion()));

        File file = new File(repoSession.getLocalRepository().getBasedir(), path);
        try {
            return file.exists() ? new File(Files.readAllLines(file.toPath(), Charset.forName("utf-8")).get(0)) : null;
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
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
