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

import com.google.inject.spi.UntargettedBinding;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
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
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.util.filter.ScopeDependencyFilter;
import org.eclipse.jetty.maven.plugin.JettyWebAppContext;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.ServerSocket;
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
public class DistMojo extends AbstractMojo {


    @Component
    private RepositorySystem repoSystem;

    @Parameter(defaultValue ="${repositorySystemSession}" ,readonly = true)
    private RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}")
    private List<RemoteRepository> remoteRepos;

    @Parameter (defaultValue = "${project.groupId}:reststop-webapp:war:${project.version}")
    private String warCoords;

    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}.${project.packaging}")
    private File pluginJar;

    @Parameter(defaultValue = "${project.build.directory}/restwork/")
    private File workDirectory;

    @Parameter(defaultValue = "${project}")
    private MavenProject mavenProject;

    @Parameter
    private List<Plugin> plugins;

    @Parameter
    private final String jettyVersion = "9.0.5.v20130815";

    private final String jettydistCoords  ="org.eclipse.jetty:jetty-distribution:tar.gz:" + jettyVersion;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        File repository = new File(workDirectory, "repository");

        LocalRepository repo = new LocalRepository(repository);
        LocalRepositoryManager manager = repoSystem.newLocalRepositoryManager(repoSession, repo);

        copyPlugins(getPlugins(), manager);

        Artifact warArifact = resolveArtifactFile(warCoords);
        copyArtifactToRepository(warArifact, manager);

        copyJetty();

        createContextXml(warArifact, manager);

    }

    private void createContextXml(Artifact warArifact, LocalRepositoryManager manager) throws MojoExecutionException {
        try {
            String xml = IOUtils.toString(getClass().getResourceAsStream("reststop.xml"), "utf-8");

            String warLocation = "../repository/" +manager.getPathForLocalArtifact(warArifact);

            xml = xml.replaceAll("RESTSTOPWAR", warLocation);

            Files.write(new File(workDirectory, "jetty/webapps/reststop.xml").toPath(), singleton(xml), Charset.forName("utf-8"));

        } catch (IOException e) {
            throw new MojoExecutionException("Failed reading reststop.xml", e);
        }
    }

    private void copyJetty() throws MojoFailureException, MojoExecutionException {
        Artifact jettyDistroArtifact = resolveArtifactFile(jettydistCoords);

        File jettyDir = new File(workDirectory, "jetty");

        if(jettyDir.exists()) {
            try {
                Files.walkFileTree(jettyDir.toPath(), new SimpleFileVisitor<Path>() {
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
                });
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

    private String coords(org.apache.maven.artifact.Artifact artifact) {
        return artifact.getGroupId() +":" + artifact.getArtifactId() +":" + artifact.getVersion();
    }

    private void copyPlugins(List<Plugin> plugins, LocalRepositoryManager manager) throws MojoFailureException, MojoExecutionException {
        if(plugins != null) {
            List<String> coords = new ArrayList<>();

            for (Plugin plugin : plugins) {
                String line = plugin.getCoords();

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
                            line += ", " + artifact.getGroupId() +":" + artifact.getArtifactId() +":" + artifact.getVersion();
                            copyArtifactToRepository(artifact, manager);
                        }
                }
                coords.add(line);
            }

            try {
                Files.write(new File(workDirectory, "plugins.txt").toPath(), coords, Charset.forName("utf-8"));
            } catch (Exception e) {
                throw new MojoExecutionException("Failed writing plugins.txt", e);
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
}
