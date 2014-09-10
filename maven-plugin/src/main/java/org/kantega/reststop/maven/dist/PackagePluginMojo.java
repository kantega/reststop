package org.kantega.reststop.maven.dist;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Zip;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.LocalRepositoryManager;

import java.io.File;

/**
 *
 */
@Mojo(name = "package-plugins",
        defaultPhase = LifecyclePhase.PREPARE_PACKAGE,
        requiresDependencyResolution = ResolutionScope.COMPILE)
public class PackagePluginMojo extends AbstractDistMojo {

    @Parameter(defaultValue = "${project.build.directory/reststop/warpack/WEB-INF/reststop}")
    private File packageDirectory;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        File repository = new File(packageDirectory, "repository");
        repository.mkdirs();
        LocalRepository repo = new LocalRepository(repository);
        LocalRepositoryManager manager = repoSystem.newLocalRepositoryManager(repoSession, repo);

        copyPlugins(getPlugins(), manager);

        writePluginsXml(new File(packageDirectory, "plugins.xml"));

    }

    @Override
    protected void attachPackage(MavenProjectHelper mavenProjectHelper, MavenProject mavenProject) throws MojoFailureException {

    }

    @Override
    protected void performPackaging() throws MojoExecutionException {

    }
}
