package org.kantega.reststop.maven.dist;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.CommandLineUtils;
import org.codehaus.plexus.util.cli.Commandline;
import org.codehaus.plexus.util.cli.StreamConsumer;

import java.io.*;

/**

 */
@Mojo(name = "dist-rpm",
        defaultPhase = LifecyclePhase.PACKAGE,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class RpmBuilder extends AbstractDistMojo {


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        super.execute();
        createRpm(new File(workDirectory, "rpm"), rootDirectory);
    }

    private String trimBothEnds(String str, String trim){
        if( str.startsWith(trim)) str = str.substring(1);
        if( str.endsWith(trim)) str = str.substring(0,str.length()-1);
        return str;
    }
        
    

    private void createRpm(File rpmDirectory, File rootDirectory) throws MojoExecutionException {


        File specs = new File(rpmDirectory, "SPECS");
        specs.mkdirs();
        File sources = new File(rpmDirectory, "SOURCES");
        sources.mkdirs();
        new File(rpmDirectory, "BUILD").mkdirs();
        new File(rpmDirectory, "BUILDROOT").mkdirs();
        new File(rpmDirectory, "RPMS").mkdirs();
        new File(rpmDirectory, "SRPMS").mkdirs();
        new File(rpmDirectory, "tmp-buildroot").mkdirs();
        new File(rpmDirectory, "buildroot").mkdirs();


        File spec = new File(specs, mavenProject.getArtifactId() + ".spec");

        writeSpecFile(spec);


        buildRpm(rpmDirectory, rootDirectory, spec);
    }

    private void buildRpm(File rpmDirectory, File rootDirectory, File spec) throws MojoExecutionException {

        Commandline commandline = new Commandline();
        commandline.setExecutable("rpmbuild");
        commandline.createArg().setValue("--target");
        commandline.createArg().setValue("noarch-redhat-linux");
        commandline.createArg().setValue("--buildroot");
        commandline.createArg().setFile(rootDirectory);
        commandline.createArg().setValue("--define");
        commandline.createArg().setValue("_tmppath " + rpmDirectory.getAbsolutePath());
        commandline.createArg().setValue("--define");
        commandline.createArg().setValue("_topdir " + rpmDirectory.getAbsolutePath());
        commandline.createArg().setValue("--define");
        commandline.createArg().setValue("_binaries_in_noarch_packages_terminate_build   0");
        commandline.createArg().setValue("-bb");
        commandline.createArg().setFile(spec);
        final StreamConsumer stdout = new LogStreamConsumer( getLog(), false);
        final StreamConsumer stderr = new LogStreamConsumer( getLog(), true);

        try {
            int status =  CommandLineUtils.executeCommandLine(commandline, stdout, stderr);
            if (status != 0)
                throw new MojoExecutionException("Failed to run rpmbuild (exitcode "+status+") See log for details");
        } catch (CommandLineException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }


        getLog().info("cd " + rpmDirectory.getAbsolutePath());
        getLog().info("rpmbuild --target noarch-redhat-linux  --quiet --buildroot " + rootDirectory.getAbsolutePath()
                + " --define \"_tmppath " +rpmDirectory.getAbsolutePath() +"\" --define \"_topdir " +rpmDirectory.getAbsolutePath() +"\" -bb " + spec.getAbsolutePath());
    }

    private void writeSpecFile(File spec) throws MojoExecutionException {

        try (PrintWriter pw = new PrintWriter(new OutputStreamWriter(new FileOutputStream(spec)))) {

            String installDir = trimBothEnds(this.installDir,"/");
            pw.println("Name: " + name);
            pw.println("Version: " + safeVersion());
            pw.println("Release: 1");
            pw.println("Summary: " + mavenProject.getDescription());
            pw.println("License: Unknown");
            pw.println("Group: Webapps/Java");
            pw.println("BuildArchitectures: noarch");
            pw.println("%description");
            pw.println("%{summary}");


            pw.println("Requires(pre): /usr/sbin/useradd, /usr/bin/getent");
            pw.println("%pre");
            pw.println("/usr/bin/getent group %{name} > /dev/null || /usr/sbin/groupadd -r %{name}");
            pw.println("/usr/bin/getent passwd %{name} > /dev/null || /usr/sbin/useradd -r -g %{name} -d /" + installDir +"/%{name}/jetty -s /bin/bash %{name}");

            pw.println("%files");
            pw.println("%defattr(0664, %{name}, %{name}, 0775)");
            pw.println("/"+installDir+"/%{name}");
            pw.println("%attr(0755, %{name}, %{name}) /"+installDir+"/%{name}/"+trimBothEnds(container,"/")+"/bin/*.sh");
            pw.println("%attr(0755, %{name}, %{name}) /etc/init.d/%{name}");


        } catch (FileNotFoundException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private class LogStreamConsumer implements StreamConsumer {
        private final Log log;
        private final boolean errorLog;

        public LogStreamConsumer(Log log, boolean error) {
            this.log = log;
            this.errorLog = error;
        }

        @Override
        public void consumeLine(String line) {

            if( !errorLog ) log.info("rpmbuild: " +line);
            else log.error("rpmbuild: " +line);
        }
    }

    private String safeVersion() {
        return mavenProject.getVersion().replace('-', '.');
    }

}
