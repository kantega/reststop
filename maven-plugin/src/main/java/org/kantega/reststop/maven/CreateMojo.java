package org.kantega.reststop.maven;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;

/**
 *
 */
@Mojo(name = "create", requiresProject = false)
public class CreateMojo extends AbstractMojo {


    @Component
    private Invoker invoker;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        PluginDescriptor pluginDescriptor = (PluginDescriptor) getPluginContext().get("pluginDescriptor");

        Map<String, String> options = getOptions();

        String pack = options.get("package");
        String groupId = options.get("groupId");
        String artifactId = options.get("artifactId");

        File directory = new File(artifactId);
        File pluginsDir = new File(directory, "plugins");
        File pluginDir = new File(pluginsDir, "helloworld");
        File webappDir = new File(directory, "webapp");
        File webappTestJettyDir = new File(new File(new File(webappDir, "src"), "test"), "jetty");

        if(directory.exists()) {
            throw new MojoFailureException("Directory already exists: " + directory);
        }
        directory.mkdirs();

        if(pluginsDir.exists()) {
            throw new MojoFailureException("Directory already exists: " + pluginsDir);
        }
        pluginsDir.mkdirs();

        if(pluginDir.exists()) {
            throw new MojoFailureException("Directory already exists: " + pluginDir);
        }
        pluginDir.mkdirs();

        if(webappDir.exists()) {
            throw new MojoFailureException("Directory already exists: " + webappDir);
        }
        webappDir.mkdirs();
        webappTestJettyDir.mkdirs();

        File pomFile = new File(directory, "pom.xml");
        File pluginPomFile = new File(pluginDir, "pom.xml");
        File pluginsPomFile = new File(pluginsDir, "pom.xml");
        File webappPomFile = new File(webappDir, "pom.xml");
        File webappTestJettyFile = new File(webappTestJettyDir, "web-override.xml");

        try {
            // root
            String pom = IOUtils.toString(getClass().getResourceAsStream("dist/template-plugin-pom.xml"), "utf-8");
            pom = pom.replace("${groupId}", groupId).
                    replace("${artifactId}", artifactId)
                    .replace("${reststopVersion}", pluginDescriptor.getVersion());

            Files.write(pomFile.toPath(), pom.getBytes("utf-8"));

            // plugins
            String pluginsPom = IOUtils.toString(getClass().getResourceAsStream("dist/template-plugin-plugins-pom.xml"), "utf-8");
            pluginsPom = pluginsPom.replace("${groupId}", groupId).
                    replace("${artifactId}", artifactId);

            Files.write(pluginsPomFile.toPath(), pluginsPom.getBytes("utf-8"));

            // plugin
            String pluginPom = IOUtils.toString(getClass().getResourceAsStream("dist/template-plugin-plugin-pom.xml"), "utf-8");
            pluginPom = pluginPom.replace("${groupId}", groupId).
                    replace("${artifactId}", artifactId);

            Files.write(pluginPomFile.toPath(), pluginPom.getBytes("utf-8"));

            List<String> classNames = asList("ExamplePlugin.java", "HelloworldResource.java", "Hello.java");

            createClasses(pack, classNames, new File(pluginDir, "src/main/java/"));

            List<String> testClassNames = asList("HelloworldResourceTest.java");

            createClasses(pack, testClassNames, new File(pluginDir, "src/test/java/"));

            new File(pluginDir, "src/main/resources").mkdirs();
            new File(pluginDir, "src/test/resources").mkdirs();

            // webapp
            String webappPom = IOUtils.toString(getClass().getResourceAsStream("dist/template-plugin-webapp-pom.xml"), "utf-8");
            webappPom = webappPom.replace("${groupId}", groupId).
                    replace("${artifactId}", artifactId);

            Files.write(webappPomFile.toPath(), webappPom.getBytes("utf-8"));


            String webOverride = IOUtils.toString(getClass().getResourceAsStream("dist/template-web-override.xml"), "utf-8");
            Files.write(webappTestJettyFile.toPath(), webOverride.getBytes("utf-8"));

        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }


        InvocationRequest request = new DefaultInvocationRequest();
        request.setPomFile(pomFile);
        request.setGoals(asList("clean","install"));

        try {
            invoker.execute(request);
        } catch (MavenInvocationException e) {
            throw new MojoExecutionException("Failed executing reststop:run on created project", e);
        }

        InvocationRequest request2 = new DefaultInvocationRequest();
        request2.setPomFile(webappPomFile);
        request2.setGoals(asList("jetty:run"));

        try {
            invoker.execute(request2);
        } catch (MavenInvocationException e) {
            throw new MojoExecutionException("Failed executing reststop:run on created project", e);
        }

    }

    private void createClasses(String pack, List<String> classNames, File sourceDir) throws IOException {
        for (String className : classNames) {
            String source = IOUtils.toString(getClass().getResourceAsStream(className +".txt"), "utf-8");

            source= source
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
            String defaultPackage = values.get("groupId") +"." + values.get("artifactId");
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

    private void readValue(Map<String, String> values, String name, String defaultValue) {
        String value = readLineWithDefault(name, defaultValue);

        values.put(name, value.isEmpty() ? defaultValue : value);
    }

    private String readLineWithDefault(String name, String defaultValue) {
        return System.console().readLine("Define value for property '%s' [ %s ] : ", name, defaultValue).trim();
    }
}
