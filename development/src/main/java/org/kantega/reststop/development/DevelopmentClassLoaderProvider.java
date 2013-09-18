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

import org.kantega.reststop.api.Reststop;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 *
 */
public class DevelopmentClassLoaderProvider {

    private DevelopmentClassloader classloader;

    private File pluginDir;
    private Reststop reststop;
    private ClassLoader parentClassLoader;


    public synchronized void start(Reststop reststop) {
        System.out.println("Starting devclassloaderprovider");
        this.parentClassLoader = reststop.getPluginParentClassLoader();
        pluginDir = new File(System.getProperty("reststopPluginDir"));
        this.reststop= reststop;
        classloader = new DevelopmentClassloader(pluginDir, Collections.<File>emptyList(), parentClassLoader);
        reststop.changePluginClassLoaders().add(classloader).commit();

    }


    public DevelopmentClassloader getClassloader() {
        return classloader;
    }

    public synchronized void redeploy(String compileClasspath, String runtimeClasspath) {
        classloader.compileJava(compileClasspath);
        classloader.copyResources();
        reststop.changePluginClassLoaders()
                .remove(classloader)
                .add(classloader = new DevelopmentClassloader(pluginDir, parseClasspath(runtimeClasspath), parentClassLoader))
                .commit();

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
