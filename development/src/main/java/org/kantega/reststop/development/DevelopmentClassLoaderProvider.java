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

import org.kantega.jexmec.ClassLoaderProvider;

import javax.tools.*;
import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;

/**
 *
 */
public class DevelopmentClassLoaderProvider implements ClassLoaderProvider {
    private static DevelopmentClassLoaderProvider instance;

    private DevelopmentClassloader classloader;



    private File pluginDir;
    private Registry registry;
    private ClassLoader parentClassLoader;

    @Override
    public synchronized void start(Registry registry, ClassLoader parentClassLoader) {
        System.out.println("Starting devclassloaderprovider");
        instance = this;

        pluginDir = new File(System.getProperty("reststopPluginDir"));

        classloader = new DevelopmentClassloader(pluginDir, Collections.<File>emptyList(), parentClassLoader);

        registry.add(singleton(classloader));
        this.registry = registry;
        this.parentClassLoader = parentClassLoader;


    }

    @Override
    public void stop() {

    }

    public static DevelopmentClassLoaderProvider getInstance() {
        return instance;
    }

    public DevelopmentClassloader getClassloader() {
        return classloader;
    }

    public synchronized void redeploy(String compileClasspath, String runtimeClasspath) {
        classloader.compileJava(compileClasspath);
        classloader.copyResources();
        registry.replace(singleton(classloader),
                singleton(classloader = new DevelopmentClassloader(pluginDir,  parseClasspath(runtimeClasspath), parentClassLoader)));

    }

    private List<File> parseClasspath(String runtimeClasspath) {
        List<File> files = new ArrayList<File>();
        for(String path : runtimeClasspath.split(File.pathSeparator)) {
            File file = new File(path);
            files.add(file);
        }
        return files;
    }


}
