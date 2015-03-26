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

package org.kantega.reststop.maven.dist;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Zip;

import java.io.File;

/**
 *
 */
@Mojo(name = "dist",
        defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class ZipBuilder extends AbstractDistMojo {

    @Override
    protected void performPackaging() throws MojoExecutionException {
        createZip(rootDirectory, getDestFile());
    }

    private File getDestFile() {
        return new File(workDirectory, distDirectory.getName() + ".zip");
    }

    private void createZip(File distDirectory, File destFile) {
        Zip zip = new Zip();
        zip.setProject(new Project());
        zip.setBasedir(distDirectory.getParentFile());
        zip.setDestFile(destFile);
        zip.execute();
    }

    @Override
    protected void attachPackage(MavenProjectHelper mavenProjectHelper, MavenProject mavenProject) throws MojoFailureException {
        mavenProjectHelper.attachArtifact(mavenProject, "zip", getDestFile());
    }
}
