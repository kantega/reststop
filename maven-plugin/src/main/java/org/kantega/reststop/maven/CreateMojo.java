/*
 * Copyright 2017 Kantega AS
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

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.descriptor.PluginDescriptor;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenInvocationException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;

/**
 * Creates a default Reststop Maven project with an example plugin called Helloworld.
 */
@Mojo(name = "create", requiresProject = false)
public class CreateMojo extends AbstractCreateMojo {


    @Component
    private Invoker invoker;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        PluginDescriptor pluginDescriptor = (PluginDescriptor) getPluginContext().get("pluginDescriptor");

        Map<String, String> options = getOptions();

        String pack = options.get("package");

        String artifactId = options.get("artifactId");
        File rootDir = new File(artifactId);
        File rootPom = new File(rootDir, "pom.xml");
        File webappDir = new File(rootDir, "webapp");
        File webappPom = new File(webappDir, "pom.xml");

        try {
            Map<String,String> tokens = new HashMap<>();
            tokens.put("${groupId}", options.get("groupId"));
            tokens.put("${artifactId}", artifactId);
            tokens.put("${reststopVersion}", pluginDescriptor.getVersion());

            // root
            createMavenModule(tokens, getClass().getResourceAsStream("dist/template-plugin-pom.xml"), rootPom);
            tokens.remove("${reststopVersion}");

            // plugins
            File pluginsDir = new File(rootDir, "plugins");
            createMavenModule(tokens, getClass().getResourceAsStream("dist/template-plugin-plugins-pom.xml"), new File(pluginsDir, "pom.xml"));

            // api plugin
            File apiPluginDir = new File(pluginsDir, "api");
            Map<String, String> tokensApi = new HashMap<>();
            tokensApi.put("${groupId}", options.get("groupId"));
            tokensApi.put("${name}", "api");
            tokensApi.put("${artifactId}", artifactId);
            tokensApi.put("${rootArtifactId}", artifactId);
            String projectVersion = "1.0-SNAPSHOT";
            tokensApi.put("${version}", projectVersion);
            createMavenModule(tokensApi, getClass().getResourceAsStream("dist/template-newplugin-pom.xml"), new File(apiPluginDir, "pom.xml"));

            new File(apiPluginDir, "src/main/resources").mkdirs();
            new File(apiPluginDir, "src/test/resources").mkdirs();
            File sourceDir = new File(apiPluginDir, "src/main/java");
            sourceDir.mkdirs();
            List<String> methods = new ArrayList<>();
            methods.add("getMessage");
            createInterface("Greeting", methods, sourceDir, pack);

            // helloworld plugin
            File pluginDir = new File(pluginsDir, "helloworld");
            createMavenModule(tokens, getClass().getResourceAsStream("dist/template-plugin-plugin-pom.xml"), new File(pluginDir, "pom.xml"));

            List<String> classNames = asList("ExamplePlugin.java", "HelloworldResource.java", "Hello.java");

            createClasses(pack, classNames, new File(pluginDir, "src/main/java/"));

            List<String> testClassNames = asList("HelloworldResourceTest.java");

            createClasses(pack, testClassNames, new File(pluginDir, "src/test/java/"));

            new File(pluginDir, "src/main/resources").mkdirs();
            new File(pluginDir, "src/test/resources").mkdirs();

            // webapp
            createMavenModule(tokens, getClass().getResourceAsStream("dist/template-plugin-webapp-pom.xml"), webappPom);

            File webappDirTest = new File(new File(new File(webappDir, "src"),"test"), "jetty");
            webappDirTest.mkdirs();

            String webOverride = IOUtils.toString(getClass().getResourceAsStream("dist/template-context.xml"), "utf-8");
            Files.write(new File(webappDirTest, "context.xml").toPath(), webOverride.getBytes("utf-8"));

            File webinfDir = webappDir.toPath().resolve("src").resolve("main").resolve("webapp").resolve("WEB-INF").toFile();
            webinfDir.mkdirs();
            String webXml = IOUtils.toString(getClass().getResourceAsStream("dist/template-web.xml"), "utf-8");
            webXml = webXml.replace("${artifactId}", artifactId);
            Files.write(new File(webinfDir, "web.xml").toPath(), webXml.getBytes("utf-8"));

            File config = new File(new File(webappDir, "src/config/"), artifactId +".conf");
            config.getParentFile().mkdirs();
            Files.write(config.toPath(), "greeting=Hello".getBytes("utf-8"));



        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }


        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(rootPom);
        request.setGoals(asList("clean","install"));

        try {
            invoker.execute(request);
        } catch (MavenInvocationException e) {
            throw new MojoExecutionException("Failed executing reststop:run on created project", e);
        }

        InvocationRequest request2 = new DefaultInvocationRequest();
        request2.setPomFile(webappPom);
        request2.setGoals(asList("reststop:run"));

        try {
            invoker.execute(request2);
        } catch (MavenInvocationException e) {
            throw new MojoExecutionException("Failed executing reststop:run on created project", e);
        }

    }

    private void createClasses(String pack, List<String> classNames, File sourceDir) throws IOException {
        for (String className : classNames) {
            String source = IOUtils.toString(getClass().getResourceAsStream(className +".txt"), "utf-8");

            source = source
                    .replace("package org.kantega.reststop.maven", "package " + pack);


            File dest = new File(new File(sourceDir,  pack.replace('.', '/')), className);
            dest.getParentFile().mkdirs();

            Files.write(dest.toPath(), source.getBytes("utf-8"));
        }
    }

    private Map<String,String> getOptions() {
        Map<String, String> values = new LinkedHashMap<>();

        do {

            readValue(values, "groupId", "com.example");
            readValue(values, "artifactId", "exampleservice");
            String defaultPackage = values.get("groupId") + "." + removeSpecialCharactersAndCapitalize(values.get("artifactId")).toLowerCase();
            String pack;
            for(;;) {
                pack = readLineWithDefault("package", defaultPackage).trim();
                if(pack.isEmpty()) pack = defaultPackage;

                Pattern p = Pattern.compile("^[a-zA-Z_\\$][\\w\\$]*(?:\\.[a-zA-Z_\\$][\\w\\$]*)*$");
                if(p.matcher(pack).matches()) {
                    break;
                }
            }

            values.put("package", pack);

            System.out.println();
            System.out.println("Please confirm configuration:");
            for (String option : values.keySet()) {
                System.console().printf("  %s = '%s'\n", option, values.get(option));
            }
        } while(!System.console().readLine(" Y: ").equalsIgnoreCase("y"));

        return values;
    }

}
