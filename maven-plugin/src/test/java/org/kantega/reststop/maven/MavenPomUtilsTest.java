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

package org.kantega.reststop.maven;

import org.apache.commons.io.FileUtils;
import org.junit.Test;

import java.io.File;
import java.net.URL;
import java.nio.file.Paths;

import static org.junit.Assert.assertTrue;

public class MavenPomUtilsTest {

    @Test
    public void testAddModuleToMavenPomFile() throws Exception {

        MavenPomUtils mavenPomUtils = new MavenPomUtils();
        URL url = MavenPomUtilsTest.class.getResource("/pom.xml");
        File source = Paths.get(url.toURI()).toFile();

        String moduleName = "module2";
        File destination = new File(source + "_");
        mavenPomUtils.addModule(source, destination, moduleName);

        String newFile = FileUtils.readFileToString(destination);
        assertTrue(newFile.contains("<module>" + moduleName + "</module>"));
        assertTrue(newFile.contains("<!-- comment -->"));
    }

    @Test
    public void testAddPluginToReststop() throws Exception {

        MavenPomUtils mavenPomUtils = new MavenPomUtils();
        URL url = MavenPomUtilsTest.class.getResource("/pom.xml");
        File source = Paths.get(url.toURI()).toFile();

        File destination = new File(source + "_");

        String groupdId = "org.kantega.reststop";
        String artifactId = "reststop-test-plugin";
        String version = "1.0.1";
        mavenPomUtils.addPluginToReststop(source, destination, groupdId, artifactId, version);

        String newFile = FileUtils.readFileToString(destination);
        assertTrue(newFile.contains("<artifactId>" + artifactId + "</artifactId>"));
        assertTrue(newFile.contains("<!-- comment -->"));

    }
}