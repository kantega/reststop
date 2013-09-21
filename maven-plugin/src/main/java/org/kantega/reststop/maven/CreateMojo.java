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
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static java.util.Collections.singleton;

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

        if(directory.exists()) {
            throw new MojoFailureException("Directory already exists: " + directory);
        }
        directory.mkdirs();

        File pomFile = new File(directory, "pom.xml");

        try {
            String pom = IOUtils.toString(getClass().getResourceAsStream("template-pom.xml"), "utf-8");
            pom = pom.replace("${groupId}", groupId).
                    replace("${artifactId}", artifactId)
                    .replace("${reststopVersion}", pluginDescriptor.getVersion());

            Files.write(pomFile.toPath(), pom.getBytes("utf-8"));


            List<String> classNames = asList("ExamplePlugin.java", "HelloworldResource.java", "Hello.java");

            createClasses(pack, classNames, new File(directory, "src/main/java/"));

            List<String> testClassNames = asList("HelloworldResourceTest.java");

            createClasses(pack, testClassNames, new File(directory, "src/test/java/"));

            File file = new File(directory, "src/main/resources/META-INF/services/ReststopPlugin/simple.txt");
            file.getParentFile().mkdirs();
            Files.write(file.toPath(), singleton(pack + ".ExamplePlugin"), Charset.forName("utf-8"));
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }


        InvocationRequest request = new DefaultInvocationRequest();

        request.setPomFile(pomFile);

        request.setGoals(Collections.singletonList("reststop:run"));

        try {
            invoker.execute(request);
        } catch (MavenInvocationException e) {
            throw new MojoExecutionException("Failed executing reststop:run on created project", e);
        }

    }

    private void createClasses(String pack, List<String> classNames, File sourceDir) throws IOException {
        for (String className : classNames) {
            String source = IOUtils.toString(getClass().getResourceAsStream(className), "utf-8");

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
                pack = System.console().readLine("%s [%s]: ", "package", defaultPackage).trim();
                if(pack.isEmpty()) pack = defaultPackage;

                Pattern p = Pattern.compile("^[a-zA-Z_\\$][\\w\\$]*(?:\\.[a-zA-Z_\\$][\\w\\$]*)*$");
                if(p.matcher(pack).matches()) {
                    break;
                }
            }

            values.put("package", pack);

            for (String option : values.keySet()) {
                System.console().printf("%s = %s\n", option, values.get(option));
            }
        } while(!System.console().readLine("OK?").isEmpty());

        return values;
    }

    private void readValue(Map<String, String> values, String name, String defaultValue) {
        String value = System.console().readLine("%s [%s]: ", name, defaultValue).trim();

        values.put(name, value.isEmpty() ? defaultValue : value);
    }
}
