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

public class StartMojo extends AbstractReststopRunMojo {

    @Override
    protected List<Plugin> getPlugins() {
        ArrayList<Plugin> plugins = new ArrayList<>(super.getPlugins());

        if(mavenProject.getPackaging().equals("jar")) {
            plugins.add(new Plugin(mavenProject.getGroupId(), mavenProject.getArtifactId(), mavenProject.getVersion()));
        }

        return plugins;
    }

    @Override
    protected void afterServerStart(Server server, int port) throws MojoFailureException {
        if(System.getProperty("wait") != null) {
            try {
                server.join();
            } catch (InterruptedException e) {
                throw new MojoFailureException(e.getMessage(), e);
            }
        }
    }
}
