package org.kantega.reststop.maven;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;

/**
 *
 */
@Mojo(name = "scan-plugins", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.COMPILE)

public class ScanForPluginsMojo extends AbstractMojo {

    @Parameter(defaultValue = "${project.build.sourceDirectory}")
    protected File sourceDirectory;

    @Parameter(defaultValue = "${basedir}/src/main/resources/META-INF/services/ReststopPlugin/simple.txt")
    protected File descriptorSource;

    @Parameter(defaultValue = "${project.build.outputDirectory}/META-INF/services/ReststopPlugin/simple.txt")
    protected File descriptorTarget;

    @Parameter(defaultValue = "${project.build.outputDirectory}/META-INF/services/ReststopPlugin/exports.txt")
    protected File exportsTarget;

    @Parameter(defaultValue = "${project.build.outputDirectory}/META-INF/services/ReststopPlugin/imports.txt")
    protected File importsTarget;

    @Parameter(defaultValue = "${project}")
    protected MavenProject mavenProject;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {


        if (!"jar".equals(mavenProject.getPackaging())) {
            return;
        }

        if(! sourceDirectory.exists()) {
            return;
        }
        try {

            final Set<String> pluginClassNames = new LinkedHashSet<>(descriptorSource.exists() ? Files.readAllLines(descriptorSource.toPath(), Charset.forName("utf-8"))
                    : Collections.<String>emptySet());

            final Set<String> exports = new LinkedHashSet<>();
            final Set<String> imports = new LinkedHashSet<>();

            List<URL> files = new ArrayList<>();

            try {
                for (String elem : mavenProject.getCompileClasspathElements()) {
                    files.add(new File(elem).toURI().toURL());
                }
            } catch (DependencyResolutionRequiredException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }

            final URLClassLoader loader = new URLClassLoader(files.toArray(new URL[files.size()]));

            final Class<?> apiClass;
            final Class<?> exportClass;
            try {
                apiClass = loader.loadClass("org.kantega.reststop.api.ReststopPlugin");
                exportClass = loader.loadClass("org.kantega.reststop.api.Export");
            } catch (ClassNotFoundException e) {
                throw new MojoExecutionException(e.getMessage(), e);
            }


            final Path rootPath = sourceDirectory.toPath();

            Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toFile().getName().endsWith(".java")) {
                        String className = rootPath.relativize(file).toString();

                        className = className.substring(0, className.indexOf(".java")).replace(File.separatorChar, '.');

                        try {
                            Class<?> clazz = loader.loadClass(className);

                            if (!clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers()) && apiClass.isAssignableFrom(clazz)) {
                                pluginClassNames.add(clazz.getName());

                                for(Field field : clazz.getDeclaredFields()) {
                                    for (Annotation annotation : field.getDeclaredAnnotations()) {
                                        if (annotation.annotationType() == exportClass) {
                                            exports.add(field.getType().getName());
                                        }
                                    }

                                }

                                Constructor<?>[] constructors = clazz.getDeclaredConstructors();
                                if(constructors.length > 1) {
                                    throw new IOException("Plugin class " + clazz.getName() + " cannot have more than one constructor");
                                }

                                if(constructors.length == 1) {
                                    for(Class paramType : constructors[0].getParameterTypes()) {
                                        imports.add(paramType.getName());
                                    }
                                }
                            }
                        } catch (ClassNotFoundException e) {
                            throw new RuntimeException(e);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            getLog().info("Plugin classes: " + pluginClassNames);

            descriptorTarget.getParentFile().mkdirs();
            Files.write(descriptorTarget.toPath(), pluginClassNames, Charset.forName("utf-8"));
            Files.write(exportsTarget.toPath(), exports, Charset.forName("utf-8"));
            Files.write(importsTarget.toPath(), imports, Charset.forName("utf-8"));

        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

}
