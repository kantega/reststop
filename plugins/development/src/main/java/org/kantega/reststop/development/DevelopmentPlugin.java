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

import org.kantega.reststop.api.DefaultReststopPlugin;
import org.kantega.reststop.api.PluginListener;
import org.kantega.reststop.api.Reststop;

import javax.servlet.ServletContext;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class DevelopmentPlugin extends DefaultReststopPlugin {
    public DevelopmentPlugin(final Reststop reststop, ServletContext servletContext) {

        List<File> compileClasspath = parseClasspath(servletContext.getInitParameter("compileClasspath"));
        List<File> runtimeClasspath = parseClasspath(servletContext.getInitParameter("runtimeClasspath"));
        List<File> testClasspath = parseClasspath(servletContext.getInitParameter("testClasspath"));


        final DevelopmentClassLoaderProvider provider = new DevelopmentClassLoaderProvider(compileClasspath, runtimeClasspath, testClasspath);
        addPluginListener(new PluginListener() {
            @Override
            public void pluginManagerStarted() {
                provider.start(reststop);
            }
        });

        addServletFilter(new RedeployFilter(provider));

    }

    private List<File> parseClasspath(String classPath) {
        List<File> files = new ArrayList<>();
        for(String path : classPath.split(File.pathSeparator)) {
            File file = new File(path);
            files.add(file);
        }
        return files;
    }
}
