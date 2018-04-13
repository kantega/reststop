/*
 * Copyright 2018 Kantega AS
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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.invoker.Invoker;
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

    @Component
    private Invoker invoker;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        Resolver resolver = new Resolver(repoSystem, repoSession, remoteRepos, getLog());
        new Deployer(resolver, invoker, getLog()).deployPlugin(mavenProject.getGroupId(), mavenProject.getArtifactId(), mavenProject.getVersion(), mavenProject.getBasedir());
        getLog().info("Plugin successfully deployed");
    }
}
