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

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Untar;
import org.apache.tools.ant.types.EnumeratedAttribute;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.util.filter.ScopeDependencyFilter;
import org.w3c.dom.Document;

import javax.xml.transform.*;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

import static java.util.Collections.singleton;

/**
 *
 */
@Mojo(name = "dist",
        defaultPhase = LifecyclePhase.PACKAGE,
requiresDependencyResolution = ResolutionScope.COMPILE)
public class DistMojo extends AbstractReststopMojo {



    @Parameter(defaultValue = "${plugin}")
    private Object plugin;

    @Parameter(defaultValue = "${project.build.directory}/reststop/dist/")
    private File workDirectory;


    @Parameter
    private final String jettyVersion = "9.0.5.v20130815";

    private final String jettydistCoords  ="org.eclipse.jetty:jetty-distribution:tar.gz:" + jettyVersion;

    @Parameter
    private final String tomcatVersion = "7.0.42";

    private final String tomcatdistCoords  ="org.apache.tomcat:tomcat:tar.gz:" + tomcatVersion;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        File repository = new File(workDirectory, "repository");
        repository.mkdirs();

        LocalRepository repo = new LocalRepository(repository);
        LocalRepositoryManager manager = repoSystem.newLocalRepositoryManager(repoSession, repo);

        copyPlugins(getPlugins(), manager);

        writePluginsXml();

        Artifact warArifact = resolveArtifactFile(warCoords);
        copyArtifactToRepository(warArifact, manager);

        copyJetty();

        createJettyContextXml(warArifact, manager);

        copyTomcat();

        createTomcatContextXml(warArifact, manager);

    }

    private void writePluginsXml() throws MojoFailureException, MojoExecutionException {
        Document pluginXmlDocument = createPluginXmlDocument(true);


        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(new DOMSource(pluginXmlDocument), new StreamResult(new File(workDirectory, "plugins.xml")));
        }
        catch (TransformerException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    private void createTomcatContextXml(Artifact warArifact, LocalRepositoryManager manager) throws MojoExecutionException {
        try {
            String xml = IOUtils.toString(getClass().getResourceAsStream("reststop-tomcat.xml"), "utf-8");

            String warLocation = "../../repository/" +manager.getPathForLocalArtifact(warArifact);

            xml = xml.replaceAll("RESTSTOPWAR", warLocation);

            File xmlFile = new File(workDirectory, "tomcat/conf/Catalina/localhost/ROOT.xml");
            xmlFile.getParentFile().mkdirs();
            Files.write(xmlFile.toPath(), singleton(xml), Charset.forName("utf-8"));
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

    }

    private void createJettyContextXml(Artifact warArifact, LocalRepositoryManager manager) throws MojoExecutionException {
        try {
            String xml = IOUtils.toString(getClass().getResourceAsStream("reststop-jetty.xml"), "utf-8");

            String warLocation = "../repository/" +manager.getPathForLocalArtifact(warArifact);

            xml = xml.replaceAll("RESTSTOPWAR", warLocation);

            Files.write(new File(workDirectory, "jetty/webapps/reststop.xml").toPath(), singleton(xml), Charset.forName("utf-8"));

        } catch (IOException e) {
            throw new MojoExecutionException("Failed reading reststop.xml", e);
        }
    }

    private void copyTomcat() throws MojoFailureException, MojoExecutionException {
        Artifact tomcatArtifact = resolveArtifactFile(tomcatdistCoords);

        File tomcatDir = new File(workDirectory, "tomcat");

        if(tomcatDir.exists()) {
            try {
                Files.walkFileTree(tomcatDir.toPath(), new DeleteDirectory());
            } catch (IOException e) {
                throw new MojoExecutionException("Failed deleteing Jetty dir", e);
            }
        }
        tomcatDir.mkdirs();


        Untar untar = new Untar();
        EnumeratedAttribute gzip = EnumeratedAttribute.getInstance(Untar.UntarCompressionMethod.class, "gzip");
        untar.setCompression((Untar.UntarCompressionMethod) gzip);
        untar.setProject(new Project());
        untar.setSrc(tomcatArtifact.getFile());
        untar.setDest(tomcatDir);
        untar.execute();


        File distDir = new File(tomcatDir, "apache-tomcat-" + tomcatVersion);
        File[] files = distDir.listFiles();
        if(files != null) {
            for (File file : files) {
                file.renameTo(new File(tomcatDir, file.getName()));
            }
        }

        distDir.delete();


        File webappsDir = new File(tomcatDir, "webapps");

        if(webappsDir.exists()) {
            try {
                Files.walkFileTree(webappsDir.toPath(), new DeleteDirectory());
            } catch (IOException e) {
                throw new MojoExecutionException("Failed deleteing Tomcat's webapps dir", e);
            }
        }
        webappsDir.mkdirs();

    }

    private void copyJetty() throws MojoFailureException, MojoExecutionException {
        Artifact jettyDistroArtifact = resolveArtifactFile(jettydistCoords);

        File jettyDir = new File(workDirectory, "jetty");

        if(jettyDir.exists()) {
            try {
                Files.walkFileTree(jettyDir.toPath(), new DeleteDirectory());
            } catch (IOException e) {
                throw new MojoExecutionException("Failed deleteing Jetty dir", e);
            }
        }
        jettyDir.mkdirs();


        Untar untar = new Untar();
        EnumeratedAttribute gzip = EnumeratedAttribute.getInstance(Untar.UntarCompressionMethod.class, "gzip");
        untar.setCompression((Untar.UntarCompressionMethod) gzip);
        untar.setProject(new Project());
        untar.setSrc(jettyDistroArtifact.getFile());
        untar.setDest(jettyDir);
        untar.execute();

        File distDir = new File(jettyDir, "jetty-distribution-" + jettyVersion);
        File[] files = distDir.listFiles();
        if(files != null) {
            for (File file : files) {
                file.renameTo(new File(jettyDir, file.getName()));
            }
        }

        distDir.delete();

        new File(jettyDir, "start.d/900-demo.ini").delete();

        try {

            Files.write(new File(jettyDir, "start.d/099-plus.ini").toPath(), "OPTIONS=plus\netc/jetty-plus.xml".getBytes("utf-8"));
            Files.write(new File(jettyDir, "start.d/100-annotations.ini").toPath(), "OPTIONS=annotations\netc/jetty-annotations.xml".getBytes("utf-8"));
        } catch (Exception e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }


    }


    private void copyPlugins(List<Plugin> plugins, LocalRepositoryManager manager) throws MojoFailureException, MojoExecutionException {
        if(plugins != null) {

            for (Plugin plugin : plugins) {

                Artifact pluginArtifact = resolveArtifactFile(plugin.getCoords());
                copyArtifactToRepository(pluginArtifact, manager);
                CollectRequest collectRequest = new CollectRequest(new Dependency(pluginArtifact, "compile"), remoteRepos);

                DependencyResult dependencyResult;
                try {
                    dependencyResult = repoSystem.resolveDependencies(repoSession, new DependencyRequest(collectRequest, new ScopeDependencyFilter("test", "provided")));
                } catch (DependencyResolutionException e) {
                    throw new MojoFailureException("Failed resolving plugin dependencies", e);
                }
                if(!dependencyResult.getCollectExceptions().isEmpty()) {
                    throw new MojoFailureException("Failed resolving plugin dependencies", dependencyResult.getCollectExceptions().get(0));
                }
                for(ArtifactResult result : dependencyResult.getArtifactResults()) {
                    Artifact artifact = result.getArtifact();
                        if(!artifact.equals(pluginArtifact)) {
                            copyArtifactToRepository(artifact, manager);
                        }
                }
            }

        }
    }

    private void copyArtifactToRepository(Artifact artifact, LocalRepositoryManager remanagerository) throws MojoExecutionException {
        String pathForLocalArtifact = remanagerository.getPathForLocalArtifact(artifact);

        Path from = artifact.getFile().toPath();
        Path to = new File(remanagerository.getRepository().getBasedir(), pathForLocalArtifact).toPath();
        try {
            to.toFile().getParentFile().mkdirs();
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new MojoExecutionException("Failed copying artifact " + artifact, e);
        }
    }

    private Artifact resolveArtifactFile(String coords) throws MojoFailureException, MojoExecutionException {
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

    public List<Plugin> getPlugins() {
        List<Plugin> plugins = new ArrayList<>();
        if(this.plugins != null) {
            plugins.addAll(this.plugins);
        }
        if(new File(mavenProject.getBasedir(), "target/classes/META-INF/services/ReststopPlugin").exists()) {
            plugins.add(new Plugin(mavenProject.getGroupId(), mavenProject.getArtifactId(), mavenProject.getVersion()));
        }
        return plugins;
    }

    private class DeleteDirectory extends SimpleFileVisitor<Path> {
        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            file.toFile().delete();
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
            dir.toFile().delete();
            return FileVisitResult.CONTINUE;
        }
    }
}
