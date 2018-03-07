package org.kantega.reststop.maven;

import org.apache.commons.io.FileUtils;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.classworlds.realm.ClassRealm;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.repository.LocalRepositoryManager;
import org.kantega.reststop.bootstrap.Bootstrap;
import org.kantega.reststop.bootstrap.BootstrapHelper;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 *
 */
@Mojo(name = "boot-start",
        defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST,
        requiresDirectInvocation = true,
        requiresDependencyResolution = ResolutionScope.TEST)
@Execute(phase = LifecyclePhase.PACKAGE)

public class StartBootMojo extends AbstractReststopRunMojo {


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {


        try {

            preExecute();

            Document pluginsXml = createPluginXmlDocument(false);

            addBootstrapClasspath(pluginsXml, repoSession.getLocalRepositoryManager());

            BootstrapHelper helper = new BootstrapHelper();

            File localRepository = repoSession.getLocalRepositoryManager().getRepository().getBasedir();

            List<URL> urls = helper.getCommonURLs(pluginsXml, localRepository);

            ClassLoader mavenHidingClassLoader = new MavenHidingClassLoader((ClassRealm) getClass().getClassLoader());

            ClassLoader classLoader = helper.createClassLoader(urls, mavenHidingClassLoader);

            ServiceLoader<Bootstrap> load = ServiceLoader.load(Bootstrap.class, classLoader);
            Iterator<Bootstrap> iterator = load.iterator();
            if(! iterator.hasNext()) {
                throw new IllegalStateException("Could not find any service instance of " + Bootstrap.class +" in class path " + urls);
            }

            List<Bootstrap> bootstraps = new ArrayList<>();

            for (Bootstrap bootstrap : load) {
                bootstraps.add(bootstrap);
            }

            for (Bootstrap bootstrap : load) {
                bootstrap.preBootstrap();
            }
            for (Bootstrap bootstrap : load) {
                bootstrap.bootstrap(new File(configDir, applicationName +".conf"), pluginsXml, localRepository, classLoader);
            }
            for (Bootstrap bootstrap : load) {
                bootstrap.postBootstrap();
            }


            FileUtils.writeStringToFile(reststopPortFile, System.getProperty("reststopPort"));

            postExecute(bootstraps);
        } catch (IOException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    protected void postExecute(List<Bootstrap> bootstraps) {
        mavenProject.setContextValue("stopHook", (Runnable) () -> {
            Collections.reverse(bootstraps);
            for (Bootstrap bootstrap : bootstraps) {
                bootstrap.shutdown();
            }
        });
    }

    protected void preExecute() {
        // Do nothing
    }

    private void addBootstrapClasspath(Document pluginXmlDocument, LocalRepositoryManager manager) throws MojoFailureException, MojoExecutionException {
        ArrayList<Dependency> deps = new ArrayList<>();
        if (containerDependencies != null) {
            deps.addAll(containerDependencies);
        }
        org.apache.maven.model.Dependency reststopCore = new org.apache.maven.model.Dependency();
        reststopCore.setGroupId("org.kantega.reststop");
        reststopCore.setArtifactId("reststop-core");
        reststopCore.setVersion(pluginVersion);

        deps.add(reststopCore);
        List<Artifact> containerArtifacts = resolveContainerArtifacts(deps);
        for (Artifact containerArtifact : containerArtifacts) {
            Element common = pluginXmlDocument.createElement("common");
            common.setAttribute("groupId", containerArtifact.getGroupId());
            common.setAttribute("artifactId", containerArtifact.getArtifactId());
            common.setAttribute("version", containerArtifact.getBaseVersion());
            common.setAttribute("fole", containerArtifact.getFile().getAbsolutePath());
            pluginXmlDocument.getDocumentElement().appendChild(common);
        }
    }

    public List<Plugin> getPlugins() {
        List<Plugin> plugins = new ArrayList<>(super.getPlugins());
        if (new File(mavenProject.getBasedir(), "target/classes/META-INF/services/ReststopPlugin").exists()) {
            plugins.add(new Plugin(mavenProject.getGroupId(), mavenProject.getArtifactId(), mavenProject.getVersion()));
        }

        plugins.addAll(Stream.of(baseBootstrapPlugins, bootstrapPlugins)
                .filter(Objects::nonNull)
                .flatMap(List::stream).collect(Collectors.toList()));

        return plugins;
    }

    private class MavenHidingClassLoader extends ClassLoader {

        private final ClassRealm classRealm;
        private String[] hiddenPackages = {
                "org.eclipse.jetty"
        };

        public MavenHidingClassLoader(ClassRealm classLoader) {
            super(classLoader);
            classRealm = classLoader;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            Class<?> clazz = super.loadClass(name, resolve);

            if(clazz.getClassLoader() == classRealm && isHiddenPackage(clazz.getName())) {
                throw new ClassNotFoundException(name);
            }
            return clazz;
        }

        @Override
        protected Package getPackage(String name) {
            Package pack = super.getPackage(name);
            return isHiddenPackage(name) ? null : pack;
        }

        @Override
        public URL getResource(String name) {
            URL resource = classRealm.getResource(name);

            if(resource == null) {
                return null;
            } else {
                URL foundResource = classRealm.findResource(name);

                if (foundResource != null && foundResource.toString().equals(resource.toString())) {
                    return isHiddenPackage(name) ? null : foundResource;
                } else {
                    return resource;
                }
            }



        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            Map<String, URL> resources = new HashMap<>();
            Enumeration<URL> e = classRealm.getResources(name);
            while(e.hasMoreElements()) {
                URL resource = e.nextElement();
                resources.put(resource.toString(), resource);
            }
            if(isHiddenPackage(name)) {
                Enumeration<URL> localResources = classRealm.findResources(name);
                while (localResources.hasMoreElements()) {
                    URL localResource = localResources.nextElement();
                    resources.remove(localResource.toString());
                }
            }
            return Collections.enumeration(resources.values());
        }

        private boolean isHiddenPackage(String name) {
            name = name.replace('/', '.');

            return !name.startsWith("org.kantega");
        }
    }
}
