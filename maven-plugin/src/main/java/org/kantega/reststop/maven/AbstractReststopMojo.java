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

import org.apache.maven.model.Exclusion;
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
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.eclipse.jetty.maven.plugin.JettyWebAppContext;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.kantega.reststop.classloaderutils.CircularDependencyException;
import org.kantega.reststop.classloaderutils.PluginInfo;
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
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import static java.util.Arrays.asList;
import static java.util.Arrays.deepEquals;

/**
 *
 */
public abstract class AbstractReststopMojo extends AbstractMojo {


    @Component
    protected RepositorySystem repoSystem;

    @Parameter(defaultValue ="${repositorySystemSession}" ,readonly = true)
    protected RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}")
    protected List<RemoteRepository> remoteRepos;

    @Parameter (defaultValue = "org.kantega.reststop:reststop-webapp:war:${plugin.version}")
    protected String warCoords;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.${project.packaging}")
    private File pluginJar;

    @Parameter(defaultValue = "${project}")
    protected MavenProject mavenProject;

    @Parameter
    protected List<Plugin> basePlugins;

    @Parameter
    protected List<Plugin> plugins;


    @Parameter (defaultValue = "${plugin.version}")
    private String pluginVersion;






    protected void customizeContext(JettyWebAppContext context) {

    }

    protected void afterServerStart(Server server, int port) throws MojoFailureException {

    }

    protected Document createPluginXmlDocument(boolean prod) throws MojoFailureException, MojoExecutionException {

        List<PluginInfo> pluginInfos = getPluginInfos();

        Document document = buildPluginsDocument(prod, pluginInfos);

        validateCircularDependencies(document);

        return document;


    }

    private Document buildPluginsDocument(boolean prod, List<PluginInfo> pluginInfos) throws MojoExecutionException {

        try {
            Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

            Element pluginsElem = doc.createElement("plugins");

            doc.appendChild(pluginsElem);

            for (PluginInfo plugin : pluginInfos) {
                Element pluginElem = doc.createElement("plugin");
                pluginsElem.appendChild(pluginElem);

                for (PluginInfo parent : plugin.getParents(pluginInfos)) {
                    Element dependsElem = doc.createElement("depends-on");
                    pluginElem.appendChild(dependsElem);
                    dependsElem.setAttribute("groupId", parent.getGroupId());
                    dependsElem.setAttribute("artifactId", parent.getArtifactId());
                    dependsElem.setAttribute("version", parent.getVersion());

                }
                for (PluginInfo provider : plugin.getServiceProviders(pluginInfos)) {
                    Element dependsElem = doc.createElement("imports-from");
                    pluginElem.appendChild(dependsElem);
                    dependsElem.setAttribute("groupId", provider.getGroupId());
                    dependsElem.setAttribute("artifactId", provider.getArtifactId());
                    dependsElem.setAttribute("version", provider.getVersion());

                }
                if(!prod) {
                    if(!plugin.getConfig().isEmpty()) {
                        Element configElem = doc.createElement("config");

                        for (String name : plugin.getConfig().stringPropertyNames()) {
                            Element propElem = doc.createElement("prop");
                            propElem.setAttribute("name", name);
                            propElem.setAttribute("value", plugin.getConfig().getProperty(name));
                            configElem.appendChild(propElem);
                        }

                        pluginElem.appendChild(configElem);
                    }
                }

                pluginElem.setAttribute("groupId", plugin.getGroupId());
                pluginElem.setAttribute("artifactId", plugin.getArtifactId());
                pluginElem.setAttribute("version", plugin.getVersion());
                if(!prod) {

                    if(plugin.getSourceDirectory() != null) {
                        pluginElem.setAttribute("sourceDirectory", plugin.getSourceDirectory().getAbsolutePath());
                    }


                    pluginElem.setAttribute("pluginFile", plugin.getFile().getAbsolutePath());
                    pluginElem.setAttribute("directDeploy", Boolean.toString(plugin.isDirectDeploy()));
                }


                List<String> scopes = prod ? Collections.singletonList(JavaScopes.RUNTIME) : asList(JavaScopes.TEST, JavaScopes.RUNTIME, JavaScopes.COMPILE);

                for(String scope : scopes) {

                    Element scopeElem = doc.createElement(scope);

                    pluginElem.appendChild(scopeElem);

                    for (org.kantega.reststop.classloaderutils.Artifact artifact : plugin.getClassPath(scope)) {
                        Element artifactElement = doc.createElement("artifact");
                        artifactElement.setAttribute("groupId", artifact.getGroupId());
                        artifactElement.setAttribute("artifactId", artifact.getArtifactId());
                        artifactElement.setAttribute("version", artifact.getVersion());

                        if(!prod) {
                            artifactElement.setAttribute("file", artifact.getFile().getAbsolutePath());
                        }

                        scopeElem.appendChild(artifactElement);
                    }


                }
            }
            return doc;
        } catch (ParserConfigurationException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private List<PluginInfo> getPluginInfos() throws MojoFailureException, MojoExecutionException {
        List<PluginInfo> pluginInfos = new ArrayList<>();

        for (Plugin plugin : getPlugins()) {
            PluginInfo info = plugin.asPluginInfo();
            pluginInfos.add(info);

            Artifact pluginArtifact = resolveArtifact(plugin.getCoords());

            info.setFile(pluginArtifact.getFile());

            for(String scope : asList(JavaScopes.TEST, JavaScopes.RUNTIME, JavaScopes.COMPILE)) {

                try {

                    ArtifactDescriptorResult descriptorResult = repoSystem.readArtifactDescriptor(repoSession, new ArtifactDescriptorRequest(pluginArtifact, remoteRepos, null));

                    CollectRequest collectRequest = new CollectRequest();

                    for (RemoteRepository repo : remoteRepos) {
                        collectRequest.addRepository(repo);
                    }
                    for (Dependency dependency : descriptorResult.getDependencies()) {
                        collectRequest.addDependency(dependency);
                    }

                    collectRequest.setManagedDependencies(descriptorResult.getManagedDependencies());


                    if(plugin.getDependencies() != null) {
                        for (org.kantega.reststop.maven.Dependency dependency : plugin.getDependencies()) {
                            List<org.eclipse.aether.graph.Exclusion> exclusions = new ArrayList<>();

                            if(dependency.getExclusions() != null) {
                                for (Exclusion exclusion : dependency.getExclusions()) {
                                    exclusions.add(new org.eclipse.aether.graph.Exclusion(exclusion.getGroupId(), exclusion.getArtifactId(), "*", "*"));
                                }
                            }
                            Dependency dep = new Dependency(new DefaultArtifact(dependency.getGroupId(),
                                    dependency.getArtifactId(), dependency.getClassifier(), dependency.getType(), dependency.getVersion()), dependency.getScope(), dependency.isOptional(), exclusions);

                            collectRequest.addDependency(dep);

                        }
                    }


                    DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, new TransitiveFilter(DependencyFilterUtils.classpathFilter(scope)));

                    DependencyResult dependencyResult = repoSystem.resolveDependencies(repoSession, dependencyRequest);

                    if(!dependencyResult.getCollectExceptions().isEmpty()) {
                        throw new MojoFailureException("Failed resolving plugin dependencies", dependencyResult.getCollectExceptions().get(0));
                    }

                    for(ArtifactResult result : dependencyResult.getArtifactResults()) {
                        Artifact artifact = result.getArtifact();
                        org.kantega.reststop.classloaderutils.Artifact pa  = new org.kantega.reststop.classloaderutils.Artifact();
                        info.getClassPath(scope).add(pa);

                        pa.setGroupId(artifact.getGroupId());
                        pa.setArtifactId(artifact.getArtifactId());
                        pa.setVersion(artifact.getVersion());

                        pa.setFile(artifact.getFile());
                    }

                } catch (DependencyResolutionException | ArtifactDescriptorException e) {
                    throw new MojoFailureException("Failed resolving plugin dependencies", e);
                }


            }
        }

        validateTransitivePluginsMissing(pluginInfos);
        validateNoPluginArtifactsOnRuntimeClasspath(pluginInfos);
        return pluginInfos;
    }

    private void validateCircularDependencies(Document document) throws MojoFailureException {
        try {
            List<PluginInfo> infos = PluginInfo.parse(document);
            PluginInfo.resolveClassloaderOrder(infos);
            PluginInfo.resolveStartupOrder(infos);
        } catch (CircularDependencyException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    private void validateNoPluginArtifactsOnRuntimeClasspath(List<PluginInfo> pluginInfos) throws MojoExecutionException, MojoFailureException {
        for (PluginInfo pluginInfo : pluginInfos) {

            Map<String, org.kantega.reststop.classloaderutils.Artifact> shouldBeProvided = new TreeMap<>();

            for (org.kantega.reststop.classloaderutils.Artifact dep : pluginInfo.getClassPath("runtime")) {


                try {
                    JarFile jar = new JarFile(dep.getFile());
                    ZipEntry entry = jar.getEntry("META-INF/services/ReststopPlugin/");
                    boolean isPlugin = entry != null;
                    jar.close();

                    if(isPlugin) {
                        shouldBeProvided.put(dep.getGroupIdAndArtifactId(), dep);
                        getLog().error("Plugin " + pluginInfo.getPluginId() +" depends on plugin artifact " + dep.getPluginId() +" which must be in <scope>provided</scope> and declared as a <plugin>!");
                        String decl = String.format("\t<plugin>\n\t\t<groupId>%s</groupId>\n\t\t<artifactId>%s</artifactId>\n\t\t<version>%s</version>\n\t</plugin>", dep.getGroupId(), dep.getArtifactId(), dep.getVersion());
                        getLog().error("Please add the following to your <plugins> section:\n" + decl);
                    }


                } catch (IOException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }

            }
            if(!shouldBeProvided.isEmpty()) {
                throw new MojoFailureException("Plugin " +pluginInfo.getPluginId() +" has a Maven <dependency> on "
                        + "one or more plugin artifacts which should be made <scope>provided</scope> and directly declared as a <plugin>: " + shouldBeProvided.values());
            }
        }
    }

    private void validateTransitivePluginsMissing(List<PluginInfo> pluginInfos) throws MojoExecutionException, MojoFailureException {

        for (PluginInfo pluginInfo : pluginInfos) {

            Map<String, org.kantega.reststop.classloaderutils.Artifact> missing = new TreeMap<>();

            for (org.kantega.reststop.classloaderutils.Artifact dep : pluginInfo.getClassPath("compile")) {


                try {
                    JarFile jar = new JarFile(dep.getFile());
                    ZipEntry entry = jar.getEntry("META-INF/services/ReststopPlugin/");
                    boolean isPlugin = entry != null;
                    jar.close();

                    if(isPlugin && !isDeclaredPlugin(dep, pluginInfos)) {
                        missing.put(dep.getGroupIdAndArtifactId(), dep);
                        File pomFile = new File(mavenProject.getBasedir(), "pom.xml");
                        getLog().error("Plugin " + pluginInfo.getPluginId() +" depends on the plugin " + dep.getPluginId() +" which is not declared as a <plugin> in " + pomFile);
                        String decl = String.format("\t<plugin>\n\t\t<groupId>%s</groupId>\n\t\t<artifactId>%s</artifactId>\n\t\t<version>%s</version>\n\t</plugin>", dep.getGroupId(), dep.getArtifactId(), dep.getVersion());
                        getLog().error("Please add the following to maven-reststop-plugin's <plugins> section in " +pomFile + ":\n" + decl);
                    }


                } catch (IOException e) {
                    throw new MojoExecutionException(e.getMessage(), e);
                }

            }
            if(!missing.isEmpty()) {
                throw new MojoFailureException("Plugin " +pluginInfo.getPluginId() +" has a Maven <dependency> on "
                        + "one or more plugin artifacts which should be directly declared as a <plugin>: " + missing.values());
            }
        }

    }

    private boolean isDeclaredPlugin(org.kantega.reststop.classloaderutils.Artifact dep, List<PluginInfo> pluginInfos) {

        for(PluginInfo declared : pluginInfos) {
            if(declared.getGroupIdAndArtifactId().equals(dep.getGroupIdAndArtifactId())) {
                return true;
            }
        }
        return false;
    }

    protected List<Plugin> getPlugins() {
        List<Plugin> plugins = new ArrayList<>();

        if(this.plugins != null) {
            plugins.addAll(this.plugins);
        }
        if(this.basePlugins != null) {
            plugins.addAll(this.basePlugins);
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


    protected File resolveArtifactFile(String coords) throws MojoFailureException, MojoExecutionException {
        return resolveArtifact(coords).getFile();
    }


    protected Artifact resolveArtifact(String coords) throws MojoFailureException, MojoExecutionException {
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

    protected void addDevelopmentPlugins(List<Plugin> plugins) {
        {
            Plugin devConsolePlugin = new Plugin("org.kantega.reststop", "reststop-development-console", pluginVersion);
            plugins.add(devConsolePlugin);
            devConsolePlugin.setDirectDeploy(false);
        }

        for (Plugin plugin : plugins) {
            plugin.setDirectDeploy(false);
        }
        {
            Plugin developmentPlugin = new Plugin("org.kantega.reststop", "reststop-development-plugin", pluginVersion);
            plugins.add(developmentPlugin);
            developmentPlugin.setDirectDeploy(true);
        }


        for (Plugin plugin : plugins) {
            plugin.setSourceDirectory(getSourceDirectory(plugin));
        }
    }

    private class TransitiveFilter implements DependencyFilter {
        private final DependencyFilter dependencyFilter;

        public TransitiveFilter(DependencyFilter dependencyFilter) {
            this.dependencyFilter = dependencyFilter;
        }

        @Override
        public boolean accept(DependencyNode node, List<DependencyNode> parents) {
            for (DependencyNode parent : parents) {
                if(!dependencyFilter.accept(parent, Collections.emptyList())) {
                    return false;
                }
            }
            return dependencyFilter.accept(node, parents);
        }
    }
}
