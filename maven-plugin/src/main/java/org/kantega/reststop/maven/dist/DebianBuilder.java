package org.kantega.reststop.maven.dist;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;

import java.io.*;
import static org.twdata.maven.mojoexecutor.MojoExecutor.*;
/**

 */
@Mojo(name = "dist-debian",
        defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class DebianBuilder extends AbstractDistMojo {


    @Parameter(defaultValue="${session}")
    private MavenSession mavenSession;

    @Parameter(defaultValue ="optional")
    private String priority;

    @Parameter(defaultValue = "Jon Doe <jon.doe@neverland.org>")
    private String maintainer;

    @Component()
    private BuildPluginManager pluginManager;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();

        build();
        executeJDeb();
    }

    private void writeConfFile(File spec) throws MojoExecutionException {
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(spec)))) {

            pw.println("Package: [[name]]");
            pw.println("Version: [[version]]");
            pw.println("Section: Java");
            pw.println("Priority: " + priority);
            pw.println("Architecture: all");
            pw.println("Maintainer: " + maintainer);
            pw.println("Description: " + mavenProject.getDescription());
            pw.println("Distribution: development");  // todo



        } catch (FileNotFoundException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

    }

    public void build() throws MojoExecutionException {

        String controldir = mavenProject.getBuild().getDirectory()+"/reststop/deb/control";
        File controlDir = new File(controldir);
        controlDir.mkdirs();
        writeConfFile(new File(controldir + "/control"));

        executeJDeb();
    }

    private void executeJDeb() throws MojoExecutionException {

        executeMojo(
                plugin("org.vafer", "jdeb", "1.0.1"),
                goal("jdeb"),
                configuration(
                        element(name("controlDir"), "${project.build.directory}/reststop/deb/control"),
                        element(name("dataSet"),
                                element(name("data"),
                                        element(name("src"), "${project.build.directory}/reststop/distRoot"),
                                        element(name("type"), "directory"),
                                        element(name("includes"), ""),
                                        element(name("excludes"), "**/*.sh,**/etc/init.d/*")
                                ),
                                element(name("data"),
                                        element(name("src"), "${project.build.directory}/reststop/distRoot"),
                                        element(name("type"), "directory"),
                                        element(name("includes"), "**/*.sh,**/etc/init.d/*"),
                                        element(name("mapper"),
                                                element(name("filemode"), "755"))
                                )

                        )

                ),
                executionEnvironment(mavenProject, mavenSession, pluginManager));
    }

}
