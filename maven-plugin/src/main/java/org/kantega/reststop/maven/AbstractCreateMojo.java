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


import com.sun.codemodel.*;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.text.WordUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public abstract class AbstractCreateMojo extends AbstractMojo {

    protected File createPluginClass(String prefixName, File sourceDir, String pack) throws MojoExecutionException {
        JCodeModel cm = new JCodeModel();
        JPackage jPackage = cm._package(pack);
        JDefinedClass dc;
        String className = removeSpecialCharactersAndCapitalize(prefixName) + "Plugin";
        try {
            dc = jPackage._class(className);
        } catch (JClassAlreadyExistsException e) {
            throw new MojoExecutionException(String.format("Generating source for %s failed, Java Class seem to already exist", className),e);
        }
        dc.constructor(JMod.PUBLIC);
        dc.annotate(org.kantega.reststop.api.Plugin.class);
        try {
            cm.build(sourceDir);
        } catch (IOException e) {
            throw new MojoExecutionException(String.format("Writing source file %s%s.java failed.", sourceDir, className),e);
        }

        File dest = sourceDir;
        for (String s : pack.split("\\.")) {
            dest = new File(dest, s);
        }

        return new File(dest, className+".java");
    }
    protected File createInterface(String name, List<String> methods, File sourceDir, String pack) throws MojoExecutionException {
        JCodeModel cm = new JCodeModel();
        JPackage jPackage = cm._package(pack);
        JDefinedClass dc;
        String className = removeSpecialCharactersAndCapitalize(name);
        try {
            dc = jPackage._class(JMod.PUBLIC, className, ClassType.INTERFACE);
        } catch (JClassAlreadyExistsException e) {
            throw new MojoExecutionException(String.format("Generating source for %s failed, Java Class seem to already exist", className),e);
        }
        for(String method : methods){
            dc.method(JMod.NONE, String.class, method);
        }
        try {
            cm.build(sourceDir);
        } catch (IOException e) {
            throw new MojoExecutionException(String.format("Writing source file %s%s.java failed.", sourceDir, className),e);
        }

        File dest = sourceDir;
        for (String s : pack.split("\\.")) {
            dest = new File(dest, s);
        }

        return new File(dest, className+".java");
    }


    protected String removeSpecialCharactersAndCapitalize(String s) {
        s = s.replaceAll("\\W", " ");
        s = WordUtils.capitalizeFully(s);
        s = s.replaceAll("\\s+","");
        return s;
    }

    protected void createMavenModule(Map<String, String> tokens, InputStream template, File destination) throws MojoFailureException, IOException {
        if(destination.getParentFile().exists()) {
            throw new MojoFailureException("Directory already exists: " + destination.getParentFile());
        }
        destination.getParentFile().mkdirs();

        String pom = IOUtils.toString(template, "utf-8");

        for (Map.Entry<String, String> entry : tokens.entrySet()) {
            pom = pom.replace(entry.getKey(), entry.getValue());
        }

        Files.write(destination.toPath(), pom.getBytes("utf-8"));
    }

    protected void readValue(Map<String, String> values, String name, String defaultValue) {
        String value = readLineWithDefault(name, defaultValue);

        values.put(name, value.isEmpty() ? defaultValue : value);
    }

    protected String readLineWithDefault(String name, String defaultValue) {
        return System.console().readLine("Define value for property '%s' [ %s ] : ", name, defaultValue).trim();
    }

}
