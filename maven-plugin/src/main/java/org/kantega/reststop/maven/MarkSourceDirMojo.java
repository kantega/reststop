package org.kantega.reststop.maven;

/**
 *
 */

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@Mojo(name = "mark-source-dir", defaultPhase = LifecyclePhase.INSTALL)
public class MarkSourceDirMojo extends AbstractMojo {
    @Parameter(defaultValue ="${repositorySystemSession}" ,readonly = true)
    protected RepositorySystemSession repoSession;

    @Parameter(defaultValue = "${project}")
    protected MavenProject mavenProject;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Artifact artifact = new DefaultArtifact(mavenProject.getGroupId(), mavenProject.getArtifactId(), "sourceDir", mavenProject.getVersion() );
        String sourceDirArtifactPath = repoSession.getLocalRepositoryManager().getPathForLocalArtifact(artifact);

        File file = new File(repoSession.getLocalRepository().getBasedir(), sourceDirArtifactPath);

        file.getParentFile().mkdirs();

        try {
            Files.write(file.toPath(), mavenProject.getBasedir().getAbsolutePath().getBytes("utf-8"));
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }
}
