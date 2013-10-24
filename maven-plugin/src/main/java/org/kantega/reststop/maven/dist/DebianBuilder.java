package org.kantega.reststop.maven.dist;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;

import java.io.*;

/**

 */
public class DebianBuilder {

    private final MavenProject mavenProject;

    public DebianBuilder(MavenProject mavenProject) {
        this.mavenProject = mavenProject;
    }

    private void writeConfFile(File spec) throws MojoExecutionException {
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(spec)))) {

            pw.println("Package: [[name]]");
            pw.println("Version: [[version]]");
            pw.println("Section: Java");
            pw.println("Priority: optional"); //todo
            pw.println("Architecture: all");
            pw.println("Maintainer: ");  // todo
            pw.println("Description: " + mavenProject.getDescription());
            pw.println("Distribution: development");  // todo



        } catch (FileNotFoundException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

    }
}
