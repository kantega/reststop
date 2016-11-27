package org.kantega.reststop.maven;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;

import java.util.List;

/**
 *
 */
@Mojo(name = "deploy", requiresProject = true)
@Execute(phase = LifecyclePhase.PACKAGE)
public class DeployMojo extends AbstractMojo {

    @Component
    protected RepositorySystem repoSystem;

    @Parameter(defaultValue ="${repositorySystemSession}" ,readonly = true)
    protected RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project.remoteProjectRepositories}")
    protected List<RemoteRepository> remoteRepos;

    @Parameter(defaultValue = "${project}", readonly = true)
    private MavenProject mavenProject;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {


        Resolver resolver = new Resolver(repoSystem, repoSession, remoteRepos, getLog());
        new Deployer(resolver, getLog()).deployPlugin(mavenProject.getGroupId(), mavenProject.getArtifactId(), mavenProject.getVersion(), mavenProject.getBasedir());
        getLog().info("Plugin successfully deployed");
    }
}
