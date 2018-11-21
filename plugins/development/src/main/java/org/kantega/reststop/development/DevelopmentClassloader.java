/*
 * Copyright 2018 Kantega AS
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
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.function.Predicate;

import static java.util.Arrays.asList;
import static java.util.Objects.isNull;

/**
 *
 */
public class DevelopmentClassloader extends PluginClassLoader {

    private final long created;
    private final File basedir;
    private final File jarFile;
    private final List<File> compileClasspath;
    private final List<File> runtimeClasspath;
    private final int version;
    private final List<File> testClasspath;

    public final static JavaCompiler compiler;

    static {
        compiler = ToolProvider.getSystemJavaCompiler();

    }

    private long lastTestCompile;
    private volatile boolean testsFailed = false;
    private final List<Class<?>> loadedClasses = new CopyOnWriteArrayList<>();
    private Set<String> usedUrls = new CopyOnWriteArraySet<>();
    private volatile boolean failed;

    public DevelopmentClassloader(PluginInfo info, File baseDir, File jarFile, List<File> compileClasspath,
                                  List<File> runtimeClasspath, List<File> testClasspath, ClassLoader parent, int version) {
        super(info, new URL[0], parent);
        this.basedir = baseDir;
        this.jarFile = jarFile;
        this.compileClasspath = compileClasspath;
        this.runtimeClasspath = runtimeClasspath;
        this.version = version;
        this.testClasspath = new ArrayList<>(testClasspath);
        try {
            if(baseDir != null && baseDir.exists()) {
                this.testClasspath.add(new File(baseDir, "target/classes"));

                addURL(new File(baseDir, "target/classes").toURI().toURL());
            } else if(jarFile != null && jarFile.exists()) {
                addURL(jarFile.toURI().toURL());
                this.testClasspath.add(jarFile);
            }
            for (File file : runtimeClasspath) {
                addURL(file.toURI().toURL());
            }
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }

        this.created = System.currentTimeMillis();
        this.lastTestCompile = this.created;

    }

    public DevelopmentClassloader(DevelopmentClassloader other, ClassLoader parentClassLoader) {
        this(other.getPluginInfo(), other.getBasedir(), other.jarFile, other.compileClasspath, other.runtimeClasspath, other.testClasspath, parentClassLoader, other.version +1);
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
        if(basedir != null) {
            File resources = new File(basedir, "src/main/resources");
            File resource = new File(resources, name);
            if (resource.exists()) {
                try {
                    return resource.toURI().toURL();
                } catch (MalformedURLException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return super.getResource(name);
    }

    public boolean isStaleSources() {

        if(jarFile.lastModified() > created) {
            return true;
        }
        for (File file : runtimeClasspath) {
            if(file.lastModified() > created) {
                return true;
            }
        }
        if(basedir == null) {
            return false;
        }

        File sourceDir = new File(basedir, "src/main/java");
        if(!sourceDir.exists()) {
            return false;
        }
        File target = new File(basedir, "target/classes");

        if(!target.exists()) {
            return false;
        }
        long newestSource = newest(sourceDir, p -> p.getFileName().toString().endsWith(".java"));
        long newestClass = newest(target, p -> p.getFileName().toString().endsWith(".class"));
        return newestSource > newestClass || newestClass > created;
    }

    public boolean isStaleTests() {
        if(isNull(basedir)) return false;

        File sources = new File(basedir, "src/test/java");
        return sources.exists() && newest(sources, p -> p.getFileName().toString().endsWith(".java")) > lastTestCompile;
    }

    private long newest(File directory, Predicate<Path> filter) {
        if(! directory.exists()) {
            return 0;
        }
        NewestFileVisitor visitor = new NewestFileVisitor(filter);
        try {
            Files.walkFileTree(directory.toPath(), visitor);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return visitor.getNewest();
    }

    public ClassLoaderAndTestClasses getTestsAndClassLoader() {
        File testSources = new File(basedir, "src/test/java");
        if(!testSources.exists()) {
            return null;
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
                Class<?> clazz = loader.loadClass(className);
                classes.add(clazz);
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }

        return new ClassLoaderAndTestClasses(loader, classes);
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

    public void setFailed(boolean failed) {
        this.failed = failed;
    }

    public boolean isFailed() {
        return failed;
    }

    private class NewestFileVisitor extends SimpleFileVisitor<Path> {

        private final Predicate<Path> matcher;
        private long newest;

        public NewestFileVisitor(Predicate<Path> matcher) {
            this.matcher = matcher;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
            if(matcher.test(file)) {
                newest = Math.max(file.toFile().lastModified(), newest);
            }
            return FileVisitResult.CONTINUE;
        }

        private long getNewest() {
            return newest;
        }
    }

    public void compileSources(StandardJavaFileManager standardFileManager) {

        if(basedir == null) {
            return;
        }
        File sourceDirectory = new File(basedir, "src/main/java");
        File outputDirectory = new File(basedir, "target/classes");
        compileJava(sourceDirectory, outputDirectory, compileClasspath, standardFileManager);

    }

    public void compileJavaTests(StandardJavaFileManager standardFileManager) {
        if(basedir == null) {
            return;
        }
        File sourceDirectory = new File(basedir, "src/test/java");
        if(sourceDirectory.exists()) {
            File outputDirectory = new File(basedir, "target/test-classes");
            compileJava(sourceDirectory, outputDirectory, testClasspath, standardFileManager);

            lastTestCompile = System.currentTimeMillis();
        }
    }


    private void compileJava(File sourceDirectory, File outputDirectory, List<File> classpath, StandardJavaFileManager fileManager) {
        List<File> sourceFiles = getCompilationUnits(sourceDirectory, newest(outputDirectory, p -> p.getFileName().toString().endsWith(".class")));

        if (!sourceFiles.isEmpty()) {

            DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();


            String cp = toPath(classpath) + File.pathSeparator + outputDirectory.getAbsolutePath();

            outputDirectory.mkdirs();

            List<String> options = new ArrayList<>(asList("-g", "-classpath", cp, "-d", outputDirectory.getAbsolutePath()));

            if(!"1.8".equals(System.getProperty("java.specification.version"))) {
                options.add("--add-modules=java.xml.ws");
            }
            JavaCompiler.CompilationTask task = compiler.getTask(null, fileManager, diagnostics, options, null, fileManager.getJavaFileObjectsFromFiles(sourceFiles));


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

    public int getVersion() {
        return version;
    }

    @Override
    public String toString() {
        return getClass().getSimpleName() + " for " + getPluginInfo().getPluginId();
    }

    class ClassLoaderAndTestClasses {
        final List<Class> testClasses;
        final ClassLoader classLoader;

        ClassLoaderAndTestClasses(ClassLoader classLoader, List<Class> classes) {
            this.classLoader = classLoader;
            this.testClasses = classes;
        }
    }
}
