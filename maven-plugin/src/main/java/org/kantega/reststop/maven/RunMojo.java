package org.kantega.reststop.maven;

import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Execute;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.eclipse.jetty.server.Server;

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

    @Override
    protected void afterServerStart(Server server) throws MojoFailureException {
        try {
            server.join();
        } catch (InterruptedException e) {
            throw new MojoFailureException(e.getMessage(), e);
        }
    }

    @Override
    protected List<Plugin> getPlugins() {
        ArrayList<Plugin> plugins = new ArrayList<>(super.getPlugins());
        plugins.add(new Plugin(mavenProject.getGroupId(), "reststop-development-plugin", mavenProject.getVersion()));
        return plugins;
    }
}
