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
        createZip(new File(rootDirectory, installDir), getDestFile());
    }

    private File getDestFile() {
        return new File(workDirectory, distDirectory.getName() + ".zip");
    }

    private void createZip(File distDirectory, File destFile) {
        Zip zip = new Zip();
        zip.setProject(new Project());
        zip.setBasedir(distDirectory);
        zip.setDestFile(destFile);
        zip.execute();
    }

    @Override
    protected void attachPackage(MavenProjectHelper mavenProjectHelper, MavenProject mavenProject) throws MojoFailureException {
        mavenProjectHelper.attachArtifact(mavenProject, "zip", getDestFile());
    }
}
