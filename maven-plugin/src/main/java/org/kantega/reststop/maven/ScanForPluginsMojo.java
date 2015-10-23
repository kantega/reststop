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

            final Class<?> exportClass;
            final Class<? extends Annotation> pluginClass;
            final Class<? extends Annotation> configClass;
            try {
                exportClass = loader.loadClass("org.kantega.reststop.api.Export");
                pluginClass = (Class<? extends Annotation>) loader.loadClass("org.kantega.reststop.api.Plugin");
                configClass = (Class<? extends Annotation>) loader.loadClass("org.kantega.reststop.api.Config");
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

                            if (isPluginAnnotated(clazz, pluginClass)) {
                                pluginClassNames.add(clazz.getName());

                                for (Field field : clazz.getDeclaredFields()) {
                                    for (Annotation annotation : field.getDeclaredAnnotations()) {
                                        if (annotation.annotationType() == exportClass) {
                                            if(field.getType() == Collection.class) {
                                                ParameterizedType genericType = (ParameterizedType) field.getGenericType();
                                                exports.add(genericType.getActualTypeArguments()[0].getTypeName());
                                            } else {
                                                exports.add(field.getType().getName());
                                            }
                                        }
                                    }

                                }

                                Constructor<?>[] constructors = clazz.getDeclaredConstructors();
                                if (constructors.length > 1) {
                                    throw new IOException("Plugin class " + clazz.getName() + " cannot have more than one constructor");
                                }

                                if (constructors.length == 1) {

                                    Constructor<?> constructor = constructors[0];
                                    Class<?>[] parameterTypes = constructor.getParameterTypes();
                                    for (int i = 0; i < parameterTypes.length; i++) {
                                        Class<?> paramType = parameterTypes[i];
                                        boolean isConfigParam = constructor.getParameters()[i].isAnnotationPresent(configClass);
                                        if(!isConfigParam) {
                                            if (paramType == Collection.class) {
                                                Type[] genericParameterTypes = constructor.getGenericParameterTypes();
                                                ParameterizedType parameterizedType = (ParameterizedType) genericParameterTypes[i];
                                                Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                                                if (actualTypeArguments != null && actualTypeArguments.length == 1) {
                                                    imports.add(actualTypeArguments[0].getTypeName());
                                                }

                                            } else {
                                                imports.add(paramType.getName());
                                            }
                                        }
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

    private boolean isPluginAnnotated(Class<?> clazz, Class<? extends Annotation> pluginClass) {
        return clazz.isAnnotationPresent(pluginClass);
    }

}
