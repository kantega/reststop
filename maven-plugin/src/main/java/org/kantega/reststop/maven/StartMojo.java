package org.kantega.reststop.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.eclipse.jetty.server.Server;

import java.util.ArrayList;
import java.util.List;

/**
 *
 */
@Mojo(name = "start",
        defaultPhase = LifecyclePhase.PRE_INTEGRATION_TEST,
        requiresDependencyResolution = ResolutionScope.RUNTIME)

public class StartMojo extends AbstractReststopMojo {

    @Override
    protected List<Plugin> getPlugins() {
        ArrayList<Plugin> plugins = new ArrayList<>(super.getPlugins());
        plugins.add(new Plugin(mavenProject.getGroupId(), mavenProject.getArtifactId(), mavenProject.getVersion()));
        return plugins;
    }

    @Override
    protected void afterServerStart(Server server) throws MojoFailureException {
        if(System.getProperty("wait") != null) {
            try {
                server.join();
            } catch (InterruptedException e) {
                throw new MojoFailureException(e.getMessage(), e);
            }
        }
    }
}
