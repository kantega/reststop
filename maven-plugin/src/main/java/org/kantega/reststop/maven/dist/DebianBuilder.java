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

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.BuildPluginManager;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;

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

    @Parameter(defaultValue ="java-common")
    private String depends;

    @Component
    private BuildPluginManager pluginManager;

    @Override
    protected void performPackaging() throws MojoExecutionException {
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
            pw.println("Depends: " + depends);
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
        writePostinstFile(new File(controldir + "/postinst"));
        writePrermFile(new File(controldir + "/prerm"));
        executeJDeb();
    }

    private void writePostinstFile(File file) throws MojoExecutionException {
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file)))) {

            pw.println("#!/bin/sh");
            pw.println("/usr/sbin/update-rc.d " + name + " defaults 90 10");
            pw.println("adduser --system --no-create-home --group " + name);
            pw.print(String.format("if id -u %s > /dev/null 2>&1; then\n" +
                    "    chown %s:%s %s\n" +
                    "fi", name, name, name, installDir + "/" + name));
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void writePrermFile(File file) throws MojoExecutionException {
        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file)))) {

            pw.println("#!/bin/sh");
            pw.println("/usr/sbin/service " + name + " stop");
            pw.println("/usr/sbin/update-rc.d -f " + name + " remove");
            // It is not recomended to remove users on uninstall: http://unix.stackexchange.com/questions/47880/how-debian-package-should-create-user-accounts#answer-147123
        } catch (FileNotFoundException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }


    private void executeJDeb() throws MojoExecutionException {

        executeMojo(
                plugin("org.vafer", "jdeb", "1.5"),
                goal("jdeb"),
                configuration(
                        element(name("controlDir"), "${project.build.directory}/reststop/deb/control"),
                        element(name("dataSet"),
                                element(name("data"),
                                        element(name("src"), "${project.build.directory}/reststop/distRoot/${project.build.finalName}"),
                                        element(name("type"), "directory"),
                                        element(name("includes"), ""),
                                        element(name("excludes"), "**/.svn,**/*.sh,etc/init.d/" + name),
                                        element(name("mapper"),
                                                element(name("type"), "perm"),
                                                element(name("filemode"), "755"),
                                                element(name("user"), name),
                                                element(name("group"), name))
                                ),
                                element(name("data"),
                                        element(name("src"), "${project.build.directory}/reststop/distRoot/${project.build.finalName}"),
                                        element(name("type"), "directory"),
                                        element(name("includes"), "**/*.sh,etc/init.d/" + name),
                                        element(name("excludes"), "**/.svn"),
                                        element(name("mapper"),
                                                element(name("type"), "perm"),
                                                element(name("filemode"), "755"))
                                )
                        )
                ),
                executionEnvironment(mavenProject, mavenSession, pluginManager));
    }

    @Override
    protected void attachPackage(MavenProjectHelper mavenProjectHelper, MavenProject mavenProject) throws MojoFailureException {
        // jdeb already attaches for us
    }
}
