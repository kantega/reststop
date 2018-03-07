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

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.shared.invoker.Invoker;
import org.eclipse.jetty.maven.plugin.JettyWebAppContext;
import org.eclipse.jetty.server.Server;

import java.awt.*;
import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 *
 */
@Mojo(name = "run",
        defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST,
        requiresDirectInvocation = true,
        requiresDependencyResolution = ResolutionScope.TEST)
@Execute(phase = LifecyclePhase.PACKAGE)
public class RunMojo extends AbstractReststopRunMojo {

    @Parameter(defaultValue = "${path}")
    private String path;

    @Parameter(defaultValue = "${openProjectDir}")
    private boolean openProjectDir;

    @Parameter(defaultValue = "localhost")
    private String hostname;

    @Component
    private Invoker invoker;


    @Parameter(defaultValue="${project.artifacts}")
    Set<org.apache.maven.artifact.Artifact> projectArtifacts;

    @Override
    protected void customizeContext(JettyWebAppContext context) {
        if("war".equals(mavenProject.getPackaging())) {
            context.setClasses(classesDirectory);
            context.setWebInfLib(getDependencyFiles());
        }

        context.addSystemClass("org.kantega.reststop.classloaderutils.");
        registerBuildSystem();
    }

    private List<File> getDependencyFiles ()
    {
        List<File> dependencyFiles = new ArrayList<File>();
        for (Iterator<org.apache.maven.artifact.Artifact> iter = projectArtifacts.iterator(); iter.hasNext(); )
        {
            org.apache.maven.artifact.Artifact artifact = (org.apache.maven.artifact.Artifact) iter.next();

            // Include runtime and compile time libraries, and possibly test libs too
            if(artifact.getType().equals("war"))
            {
                continue;
            }

            if (org.apache.maven.artifact.Artifact.SCOPE_PROVIDED.equals(artifact.getScope()))
                continue; //never add dependencies of scope=provided to the webapp's classpath (see also <useProvidedScope> param)

            if (org.apache.maven.artifact.Artifact.SCOPE_TEST.equals(artifact.getScope()))
                continue; //only add dependencies of scope=test if explicitly required

            dependencyFiles.add(artifact.getFile());
            getLog().debug( "Adding artifact " + artifact.getFile().getName() + " with scope "+artifact.getScope()+" for WEB-INF/lib " );
        }

        return dependencyFiles;
    }


    @Override
    protected void afterServerStart(Server server, int port) throws MojoFailureException {
        try {
            if( hostname == null || hostname.length() == 0)
                hostname = "localhost";
            openInBrowser("http://"+ hostname + ":" + port);
            server.join();
        } catch (InterruptedException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    private void openInBrowser(String url) throws MojoFailureException {
        try {
            if (Desktop.isDesktopSupported()) {
                if (path != null) {
                    url += "/" + path;
                }
                DesktopApi.browse(new URI(url));
                if (openProjectDir) {
                    DesktopApi.open(mavenProject.getBasedir());
                }
            }

        } catch (URISyntaxException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    @Override
    protected List<Plugin> getPlugins() {
        List<Plugin> plugins = new ArrayList<>(super.getPlugins());

        if(mavenProject.getPackaging().equals("jar")) {
            Plugin projectPlugin = new Plugin(mavenProject.getGroupId(), mavenProject.getArtifactId(), mavenProject.getVersion());
            projectPlugin.setSourceDirectory(mavenProject.getBasedir());
            plugins.add(projectPlugin);
        }

        addDevelopmentPlugins(plugins);


        return plugins;
    }

}
