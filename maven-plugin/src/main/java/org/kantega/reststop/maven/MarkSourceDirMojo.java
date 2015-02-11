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

/**
 *
 */

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
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
