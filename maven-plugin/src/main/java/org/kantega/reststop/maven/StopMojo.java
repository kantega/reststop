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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.util.log.Log;

import java.io.IOException;
import java.net.SocketException;
import java.net.URL;

/**
 *
 */
@Mojo(name = "stop", defaultPhase = LifecyclePhase.POST_INTEGRATION_TEST)
public class StopMojo extends AbstractMojo {


    @Parameter(defaultValue = "${project}")
    private MavenProject mavenProject;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        getLog().info("Stopping Reststop..");

        int reststopPort = Integer.parseInt(mavenProject.getProperties().getProperty("reststopPort"));

        try {
            String url = "http://localhost:" + reststopPort + "/shutdown";
            new URL(url).openStream();
        }  catch (IOException e) {
            Log.getLog().ignore(e);
        }


        Server server = (Server) mavenProject.getContextValue("jettyServer");

        try {
            server.join();
        } catch (InterruptedException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }


    }

}
