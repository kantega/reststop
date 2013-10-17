/*
 * Copyright 2013 Kantega AS
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

package org.kantega.reststop.development;

import org.kantega.reststop.classloaderutils.Artifact;
import org.kantega.reststop.classloaderutils.PluginClassLoader;
import org.kantega.reststop.classloaderutils.PluginInfo;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.jar.Manifest;

import static java.util.Arrays.asList;

/**
 *
 */
public class DevelopmentClassloader extends PluginClassLoader{
    private final long created;
    private final File basedir;
    private final List<File> compileClasspath;
    private final List<File> runtimeClasspath;
    private final List<File> testClasspath;

    private final static JavaCompiler compiler;
    private final StandardJavaFileManager fileManager;

    private final StandardJavaFileManager testFileManager;

    static {
        compiler = ToolProvider.getSystemJavaCompiler();

    }

    private long lastTestCompile;
    private volatile boolean testsFailed = false;
    private final List<Class<?>> loadedClasses = new CopyOnWriteArrayList<>();
    private Set<String> usedUrls = new CopyOnWriteArraySet<>();

    public DevelopmentClassloader(PluginInfo info, File baseDir, List<File> compileClasspath, List<File> runtimeClasspath, List<File> testClasspath, ClassLoader parent) {
        super(info, new URL[0], parent);
        this.basedir = baseDir;
        this.compileClasspath = compileClasspath;
        this.runtimeClasspath = runtimeClasspath;
        this.testClasspath = new ArrayList<>(testClasspath);
        this.testClasspath.add(new File(baseDir, "target/classes"));
        try {
            addURL(new File(baseDir, "target/classes").toURI().toURL());
            for (File file : runtimeClasspath) {
                addURL(file.toURI().toURL());
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
        this.created = System.currentTimeMillis();
        this.lastTestCompile = this.created;

        fileManager = compiler.getStandardFileManager(null, null, null);

        testFileManager = compiler.getStandardFileManager(null, null, null);

    }

    public DevelopmentClassloader(DevelopmentClassloader other, ClassLoader parentClassLoader) {
        this(other.getPluginInfo(), other.getBasedir(), other.compileClasspath, other.runtimeClasspath, other.testClasspath, parentClassLoader);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        Class<?> clazz = super.findClass(name);
        if(clazz.getClassLoader() == this) {
            this.loadedClasses.add(clazz);
            ProtectionDomain protectionDomain = clazz.getProtectionDomain();
            if(protectionDomain != null) {
                CodeSource codeSource = protectionDomain.getCodeSource();
                if(codeSource != null) {
                    URL location = codeSource.getLocation();
                    if(location != null) {
                        this.usedUrls.add(location.toString());
                    }
                }
            }

        }
        return clazz;
    }

    public List<Class<?>> getLoadedClasses() {
        return loadedClasses;
    }

    public Set<String> getUsedUrls() {
        return usedUrls;
    }

    public List<Artifact> getUnusedArtifacts() {

        List<Artifact> artifacts = new ArrayList<>();

        for (Artifact artifact : artifacts) {
            if(isUnused(artifact)) {
                artifacts.add(artifact);
            }
        }
        return artifacts;
    }

    public boolean isUnused(Artifact artifact) {
        try {
            return !usedUrls.contains(artifact.getFile().toURI().toURL().toString());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public URL getResource(String name) {
        File resources = new File(basedir, "src/main/resources");
        File resource = new File(resources, name);
        if(resource.exists()) {
            try {
                return resource.toURI().toURL();
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
        return super.getResource(name);
    }

    public boolean isStaleSources() {

        return newest(new File(basedir, "src/main/java")) > created;
    }

    public boolean isStaleTests() {
        File sources = new File(basedir, "src/test/java");
        return sources.exists() && newest(sources) > lastTestCompile;
    }

    private long newest(File directory) {
        if(! directory.exists()) {
            return 0;
        }
        NewestFileVisitor visitor = new NewestFileVisitor();
        try {
            Files.walkFileTree(directory.toPath(), visitor);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return visitor.getNewest();
    }

    public List<Class> getTestClasses() {
        File testSources = new File(basedir, "src/test/java");
        if(!testSources.exists()) {
            return Collections.emptyList();
        }

        List<URL> urls = new ArrayList<>();


        try {
            for (File file : testClasspath) {

                urls.add(file.toURI().toURL());

            }
            urls.add(new File(basedir, "target/test-classes").toURI().toURL());
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        URLClassLoader loader = new URLClassLoader(urls.toArray(new URL[urls.size()]), getClass().getClassLoader());

        final List<String> classNames = new ArrayList<>();
        try {
            final Path root = testSources.toPath();
            Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String name = file.toFile().getName();
                    if(name.endsWith("Test.java") || name.endsWith("IT.java")) {
                        String sourceFile = root.relativize(file).toString();
                        classNames.add(sourceFile.replace(File.separatorChar, '.').substring(0, sourceFile.length()-".java".length()));
                    }
                   return  FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        List<Class> classes = new ArrayList<>();
        for (String className : classNames) {
            try {
                classes.add(loader.loadClass(className));
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
        return classes;
    }

    public File getBasedir() {
        return basedir;
    }

    public void testsFailed() {
        testsFailed = true;
    }

    public void testsPassed() {
        testsFailed = false;
    }

    public boolean hasFailingTests() {
        return testsFailed;
    }

    private class NewestFileVisitor extends SimpleFileVisitor<Path> {

        private long newest;

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            newest = Math.max(file.toFile().lastModified(), newest);
            return FileVisitResult.CONTINUE;
        }

        private long getNewest() {
            return newest;
        }
    }

    public void compileSources() {

        File sourceDirectory = new File(basedir, "src/main/java");
        File outputDirectory = new File(basedir, "target/classes");
        List<File> classpath = compileClasspath;


        compileJava(sourceDirectory, outputDirectory, classpath, fileManager);
    }

    public void compileJavaTests() {
        File sourceDirectory = new File(basedir, "src/test/java");
        if(sourceDirectory.exists()) {
            File outputDirectory = new File(basedir, "target/test-classes");
            List<File> classpath = testClasspath;

            compileJava(sourceDirectory, outputDirectory, classpath, testFileManager);

            lastTestCompile = System.currentTimeMillis();
        }
    }


    private void compileJava(File sourceDirectory, File outputDirectory, List<File> classpath, StandardJavaFileManager manager) {
        List<File> sourceFiles = getCompilationUnits(sourceDirectory, newest(outputDirectory));

        if (!sourceFiles.isEmpty()) {

            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();


            String cp = toPath(classpath) + File.pathSeparator + outputDirectory.getAbsolutePath();

            outputDirectory.mkdirs();

            List<String> options = asList("-g", "-classpath", cp, "-d", outputDirectory.getAbsolutePath());

            JavaCompiler.CompilationTask task = compiler.getTask(null, manager, diagnostics, options, null, fileManager.getJavaFileObjectsFromFiles(sourceFiles));

            boolean success = task.call();

            if (!success) {
                throw new JavaCompilationException(diagnostics.getDiagnostics());
            }
        }
    }

    private String toPath(List<File> compileClasspath) {
        StringBuilder sb = new StringBuilder();
        for (File file : compileClasspath) {
            if(sb.length() != 0) {
                sb.append(File.pathSeparator);
            }
            sb.append(file.getAbsolutePath());
        }
        return sb.toString();
    }

    public void copySourceResorces() {
        final Path fromDirectory = new File(basedir, "src/main/resources/").toPath();
        final Path toDirectory = new File(basedir, "target/classes").toPath();

        copyResources(fromDirectory, toDirectory);
    }

    public void copyTestResources() {
        final Path fromDirectory = new File(basedir, "src/test/resources/").toPath();
        final Path toDirectory = new File(basedir, "target/test-classes").toPath();

        copyResources(fromDirectory, toDirectory);
    }

    private void copyResources(final Path fromDirectory, final Path toDirectory) {
        if(! Files.exists(fromDirectory)) {
            return;
        }
        try {
            Files.walkFileTree(fromDirectory, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path target = toDirectory.resolve(fromDirectory.relativize(file));
                    target.toFile().getParentFile().mkdirs();
                    Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private List<File> getCompilationUnits(File sourceDirectory, final long newestClass) {

        final List<File> compilationUnits = new ArrayList<>();

        try {
            Files.walkFileTree(sourceDirectory.toPath(), new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (file.toFile().getName().endsWith(".java") && file.toFile().lastModified() > newestClass) {
                        compilationUnits.add(file.toFile());
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
            return compilationUnits;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " for " + getPluginInfo().getPluginId();
    }
}
