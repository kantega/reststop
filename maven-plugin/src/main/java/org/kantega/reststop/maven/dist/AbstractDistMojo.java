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

package org.kantega.reststop.maven.dist;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.tools.ant.DirectoryScanner;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Untar;
import org.apache.tools.ant.types.EnumeratedAttribute;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.eclipse.aether.resolution.ArtifactResult;
import org.eclipse.aether.resolution.DependencyRequest;
import org.eclipse.aether.resolution.DependencyResolutionException;
import org.eclipse.aether.resolution.DependencyResult;
import org.eclipse.aether.util.filter.ScopeDependencyFilter;
import org.kantega.reststop.maven.AbstractReststopMojo;
import org.kantega.reststop.maven.Plugin;
import org.w3c.dom.Document;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static java.util.Collections.singleton;

/**
 *
 */
public abstract class AbstractDistMojo extends AbstractReststopMojo {

    @Parameter(defaultValue = "${plugin}")
    private Object plugin;

    @Parameter(defaultValue = "${project.build.directory}/reststop/")
    protected File workDirectory;

    @Parameter(defaultValue = "${project.basedir}/src/dist")
    protected File distSrc;

    @Parameter
    protected String jettyVersion;

    private final String jettydistPrefix = "org.eclipse.jetty:jetty-distribution:tar.gz:";

    @Parameter(defaultValue="7.0.42")
    protected String tomcatVersion;

    private final String tomcatdistPrefix = "org.apache.tomcat:tomcat:tar.gz:";

    @Parameter(defaultValue = "${project.artifactId}")
    protected String name;

    @Parameter(defaultValue = "/")
    protected String contextPath;

    @Parameter(defaultValue = "jetty")
    protected String container;

    @Parameter(defaultValue = "/opt")
    protected String installDir;

    protected File rootDirectory;
    protected File distDirectory;

    @Parameter()
    private boolean resolveSources;

    @Parameter(defaultValue = "true")
    private boolean attach;

    @Component
    private MavenProjectHelper mavenProjectHelper;

    @Parameter
    protected List<Resource> resources;

    @Parameter
    private List<Plugin> distributionPlugins;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        if ("tomcat".compareTo(container) != 0 && "jetty".compareTo(container) != 0)
            throw new MojoFailureException(container + " not supported. Try 'jetty' or 'tomcat' ");

        initDirectories();

        File repository = new File(distDirectory, "repository");
        repository.mkdirs();

        File confDir = new File(distDirectory, "conf");
        confDir.mkdirs();
        new File(confDir, ".keep_empty_dir");


        LocalRepository repo = new LocalRepository(repository);
        LocalRepositoryManager manager = repoSystem.newLocalRepositoryManager(repoSession, repo);

        copyPlugins(getPlugins(), manager);

        writePluginsXml(new File(distDirectory, "plugins.xml"));

        Artifact warArifact = resolveArtifact(warCoords);
        copyArtifactToRepository(warArifact, manager);

        File containerDistrDir = new File(distDirectory, container);
        if ("jetty".compareTo(this.container) == 0) {

            copyJetty(containerDistrDir);

            createJettyContextXml(name, warArifact, manager, new File(containerDistrDir, "webapps/reststop.xml"));

            createJettyServicesFile(rootDirectory);
        } else if ("tomcat".compareTo(this.container) == 0) {
            copyTomcat(containerDistrDir);

            createTomcatContextXml(name, warArifact, manager, new File(containerDistrDir, "conf/Catalina/localhost/ROOT.xml"));
        } else
            throw new MojoExecutionException("Unknown container " + this.container);

        copyOverridingConfig();

        copyResources();


        performPackaging();

        if(attach) {
            attachPackage(mavenProjectHelper, mavenProject);
        }
    }

    protected void initDirectories() {
        this.rootDirectory = new File(workDirectory, "distRoot/" + name + "-" + mavenProject.getVersion());
        this.distDirectory = new File(rootDirectory, installDir + "/" + name);
        distDirectory.mkdirs();
    }

    private void copyResources() {
        if(resources != null) {
            for (Resource resource : resources) {

                String[] includedFiles = getIncludedFiles(resource);

                if(includedFiles != null) {
                    for (String includedFile : includedFiles) {

                        String target = resource.getTargetDirectory() == null ? includedFile : resource.getTargetDirectory() +"/" + includedFile;
                        File source = new File(resource.getDirectory(), includedFile);
                        File dest = new File(rootDirectory, target);
                        dest.getParentFile().mkdirs();
                        try {
                            FileUtils.copyFile(source, dest);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                }
            }
        }
    }

    protected abstract void attachPackage(MavenProjectHelper mavenProjectHelper, MavenProject mavenProject) throws MojoFailureException;

    protected abstract void performPackaging() throws MojoExecutionException;

    private void copyOverridingConfig() throws MojoExecutionException {
        try {
            if (this.distSrc.exists()) {
                getLog().info("Copying local configuration from "  + distSrc.getCanonicalPath());
                FileUtils.copyDirectory(this.distSrc, distDirectory);
            }
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }
    


    protected void writePluginsXml(File xmlFile) throws MojoFailureException, MojoExecutionException {
        Document pluginXmlDocument = createPluginXmlDocument(true);


        try {
            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
            transformer.transform(new DOMSource(pluginXmlDocument), new StreamResult(xmlFile));
        } catch (TransformerException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    private void createTomcatContextXml(String name, Artifact warArifact, LocalRepositoryManager manager, File contextFile) throws MojoExecutionException {
        try {
            String xml = IOUtils.toString(getClass().getResourceAsStream("reststop-tomcat.xml"), "utf-8");

            String warLocation = "../../repository/" + manager.getPathForLocalArtifact(warArifact);

            xml = xml.replaceAll("WEBAPP", name);
            xml = xml.replaceAll("RESTSTOPWAR", warLocation);
            xml = xml.replaceAll("CONTEXTPATH", contextPath);

            contextFile.getParentFile().mkdirs();
            Files.write(contextFile.toPath(), singleton(xml), Charset.forName("utf-8"));
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

    }

    private void createJettyContextXml(String name, Artifact warArifact, LocalRepositoryManager manager, File contextXml) throws MojoExecutionException {
        try {
            String xml = IOUtils.toString(getClass().getResourceAsStream("reststop-jetty.xml"), "utf-8");

            String warLocation = "../repository/" + manager.getPathForLocalArtifact(warArifact);

            xml = xml.replaceAll("WEBAPP", name);
            xml = xml.replaceAll("RESTSTOPWAR", warLocation);
            xml = xml.replaceAll("CONTEXTPATH", contextPath);

            Files.write(contextXml.toPath(), singleton(xml), Charset.forName("utf-8"));

        } catch (IOException e) {
            throw new MojoExecutionException("Failed reading reststop.xml", e);
        }
    }

    private void copyTomcat(File tomcatDir) throws MojoFailureException, MojoExecutionException {
        Artifact tomcatArtifact = resolveArtifact(tomcatdistPrefix+tomcatVersion);

        if (tomcatDir.exists()) {
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
        if (files != null) {
            for (File file : files) {
                file.renameTo(new File(tomcatDir, file.getName()));
            }
        }

        distDir.delete();


        File webappsDir = new File(tomcatDir, "webapps");

        if (webappsDir.exists()) {
            try {
                Files.walkFileTree(webappsDir.toPath(), new DeleteDirectory());
            } catch (IOException e) {
                throw new MojoExecutionException("Failed deleteing Tomcat's webapps dir", e);
            }
        }
        webappsDir.mkdirs();

    }

    private void copyJetty(File jettyDir) throws MojoFailureException, MojoExecutionException {
        Artifact jettyDistroArtifact = resolveArtifact(jettydistPrefix+getJettyVersion());

        if (jettyDir.exists()) {
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

        File distDir = new File(jettyDir, "jetty-distribution-" + getJettyVersion());
        File[] files = distDir.listFiles();
        if (files != null) {
            for (File file : files) {
                file.renameTo(new File(jettyDir, file.getName()));
            }
        }

        distDir.delete();

    }

    private String getJettyVersion() throws MojoExecutionException {
        if(jettyVersion != null) {
            return jettyVersion;
        } else {
            Properties props = new Properties();
            try {
                props.load(getClass().getClassLoader().getResourceAsStream("META-INF/maven/org.eclipse.jetty/jetty-webapp/pom.properties"));
                String version = props.getProperty("version");
                return version;
            } catch (IOException e) {
                throw new MojoExecutionException("Can't load pom.properties", e);
            }
        }
    }

    protected void createJettyServicesFile(File distDirectory) throws MojoFailureException, MojoExecutionException {
        try {
            String serviceFile = IOUtils.toString(getClass().getResourceAsStream("template-service-jetty.sh"), "utf-8");
            serviceFile = serviceFile.replaceAll("RESTSTOPINSTDIR", installDir);
            serviceFile = serviceFile.replaceAll("RESTSTOPNAME", name); // use default

            File initDDir = new File(distDirectory, "etc/init.d");
            initDDir.mkdirs();

            Files.write(new File(initDDir, name).toPath(), singleton(serviceFile), Charset.forName("utf-8"));
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }


    protected void copyPlugins(List<Plugin> plugins, LocalRepositoryManager manager) throws MojoFailureException, MojoExecutionException {
        if (plugins != null) {

            for (Plugin plugin : plugins) {

                Artifact pluginArtifact = resolveArtifact(plugin.getCoords());
                copyArtifactToRepository(pluginArtifact, manager);
                resolveSources(pluginArtifact, manager);
                CollectRequest collectRequest = new CollectRequest(new Dependency(pluginArtifact, "compile"), remoteRepos);

                DependencyResult dependencyResult;
                try {
                    dependencyResult = repoSystem.resolveDependencies(repoSession, new DependencyRequest(collectRequest, new ScopeDependencyFilter("test", "provided")));
                } catch (DependencyResolutionException e) {
                    throw new MojoFailureException("Failed resolving plugin dependencies", e);
                }
                if (!dependencyResult.getCollectExceptions().isEmpty()) {
                    throw new MojoFailureException("Failed resolving plugin dependencies", dependencyResult.getCollectExceptions().get(0));
                }
                for (ArtifactResult result : dependencyResult.getArtifactResults()) {
                    Artifact artifact = result.getArtifact();
                    if (!artifact.equals(pluginArtifact)) {
                        copyArtifactToRepository(artifact, manager);
                        resolveSources(artifact, manager);
                    }
                }
            }

        }
    }

    private void resolveSources(Artifact artifact, LocalRepositoryManager manager) throws MojoFailureException, MojoExecutionException {
        if (resolveSources) {
            String coords = artifact.getGroupId() + ":" + artifact.getArtifactId() + ":jar:sources:" + artifact.getVersion();
            try {
                Artifact sourceArtifact = resolveArtifact(coords);
                copyArtifactToRepository(sourceArtifact, manager);
            } catch (MojoFailureException | MojoExecutionException e) {

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


    public List<Plugin> getPlugins() {
        List<Plugin> plugins = new ArrayList<>(super.getPlugins());
        if (new File(mavenProject.getBasedir(), "target/classes/META-INF/services/ReststopPlugin").exists()) {
            plugins.add(new Plugin(mavenProject.getGroupId(), mavenProject.getArtifactId(), mavenProject.getVersion()));
        }
        if (distributionPlugins != null) {
            plugins.addAll(distributionPlugins);
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

    protected String[] getIncludedFiles(Resource resource) {
        DirectoryScanner scanner = new DirectoryScanner();
        scanner.addDefaultExcludes();
        scanner.setBasedir(resource.getDirectory());
        scanner.addExcludes(resource.getExcludes());
        scanner.setIncludes(resource.getIncludes());

        scanner.scan();

        return scanner.getIncludedFiles();
    }
}
