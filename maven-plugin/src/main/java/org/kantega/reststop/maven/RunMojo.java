package org.kantega.reststop.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.jetty.maven.plugin.JettyWebAppContext;
import org.eclipse.jetty.server.Server;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
@Mojo(name = "run",
        defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST,
        requiresDirectInvocation = true,
        requiresDependencyResolution = ResolutionScope.TEST)
@Execute(phase = LifecyclePhase.PACKAGE)
public class RunMojo extends AbstractReststopMojo {

    @Parameter(defaultValue = "${path}")
    private String path;

    @Parameter(defaultValue = "${openProjectDir}")
    private boolean openProjectDir;

    @Override
    protected void afterServerStart(Server server, int port) throws MojoFailureException {
        try {
            openInBrowser(port);
            server.join();
        } catch (InterruptedException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    private void openInBrowser(int port) throws MojoFailureException {
        try {
        if(Desktop.isDesktopSupported()) {
            String u = "http://localhost:" + port;
            if(path != null) {
                u +="/" + path;
            }
            Desktop.getDesktop().browse(new URI(u));
            if(openProjectDir) {
                Desktop.getDesktop().open(mavenProject.getBasedir());
            }
        }} catch (IOException | URISyntaxException e) {
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
