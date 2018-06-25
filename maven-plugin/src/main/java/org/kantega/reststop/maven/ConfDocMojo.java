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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.kantega.reststop.classloaderutils.config.PluginConfigParam;
import org.kantega.reststop.classloaderutils.config.PluginConfigParams;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.*;
import java.util.*;
import java.util.function.Predicate;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

@Mojo(name = "conf-doc", defaultPhase = LifecyclePhase.PROCESS_CLASSES, requiresDependencyResolution = ResolutionScope.COMPILE)
public class ConfDocMojo extends AbstractReststopMojo {

    @Parameter(defaultValue = "${project.build.directory}/example.conf")
    private File exampleConfigFile;

    @Parameter(defaultValue = "${project.build.directory}/config-params.html")
    private File htmlDocumentationFile;

    @Parameter(required = true)
    private String applicationName;

    @Parameter(defaultValue = "${basedir}/src/config")
    private File configDir;

    @Parameter(defaultValue = "false")
    private boolean suppressWarnings;


    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {

        try {
            DocumentBuilder documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();

            exampleConfigFile.getParentFile().mkdirs();

            Map<String, List<PluginConfig>> configs = findConfigs(documentBuilder);

            Properties properties = validateConfiguration(configs);
            writeExampleConfig(configs);
            writeHtmlDoc(configs, properties);


        } catch (IOException | ParserConfigurationException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private void writeHtmlDoc(Map<String, List<PluginConfig>> configMap, Properties properties) throws FileNotFoundException, UnsupportedEncodingException {
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(htmlDocumentationFile), "iso-8859-1"))) {

            out.println("<html>");
            out.print("<head><style>");
            out.println("table {border-collapse: collapse;border: 1px solid lightgray} td {border: 1px solid lightgray}");
            out.println(".value {font-family:monospace}");
            out.println(".optional {font-style:italic}");
            out.print("</style></head>");
            out.println("<body>");
            out.println("<h1>Configuration</h1>");
            out.println("<table>");
            out.println("<tr>");
            out.print("<th>");
            out.print("Property name");
            out.println("</th>");
            out.print("<th>");
            out.print("Description");
            out.println("</th>");
            out.print("<th>");
            out.print("Default");
            out.println("</th>");
            out.print("<th>");
            out.print("Test value");
            out.println("</th>");
            out.println("</tr>");

            for (Map.Entry<String, List<PluginConfig>> entry : configMap.entrySet()) {
                out.println("<tr>");
                out.println("<td style='font-weight:bold' colspan=4>");
                out.print(entry.getKey().split(":")[1]);
                out.println("</td>");
                out.println("</tr>");


                for (PluginConfig config : entry.getValue()) {

                    Collections.sort(config.getConfigParams(), Comparator.comparing(PluginConfigParam::isOptional).thenComparing(PluginConfigParam::getParamName));

                    for (PluginConfigParam param : config.getConfigParams()) {
                        out.println("<tr>");
                        out.print("<td");
                        if (param.isOptional()) {
                            out.print(" class=optional");
                        }
                        out.println(">");

                        out.print(param.getParamName());
                        out.println("</td>");

                        out.println("<td style='font-size:small'>");
                        if (param.hasDocumentation()) {
                            out.print(param.getDoc());
                        }
                        out.println("</td>");

                        out.println("<td class=value>");
                        out.print(param.getDefaultValue());
                        out.println("</td>");

                        out.println("<td class=value>");
                        String value = properties.getProperty(param.getParamName());
                        if (value != null) {
                            out.print(value);
                        }
                        out.println("</td>");
                        out.println("</tr>");
                    }
                }


            }
            out.println("</table>");
            out.println("</body>");
            out.println("</html>");
        }
    }

    private Properties validateConfiguration(Map<String, List<PluginConfig>> configMap) throws IOException, MojoFailureException {
        Properties props = new Properties();
        String configFileName = applicationName + ".conf";
        props.load(new FileInputStream(new File(configDir, configFileName)));

        List<ParamContext> missingParameters = new ArrayList<>();

        Set<String> knownProperties = new HashSet<>();
        for (Map.Entry<String, List<PluginConfig>> entry : configMap.entrySet()) {

            for (PluginConfig config : entry.getValue()) {
                for (PluginConfigParam param : config.getConfigParams()) {
                    if (param.getType().equals("java.util.Properties")) {
                        continue;
                    }
                    knownProperties.add(param.getParamName());
                    String value = props.getProperty(param.getParamName());
                    if (value == null && param.isRequired() && !param.hasDefaultValue()) {
                        missingParameters.add(new ParamContext(entry.getKey(), config, param));
                    }
                }
            }
        }

        if (!missingParameters.isEmpty()) {
            StringBuilder message = new StringBuilder(String.format("Config file %s is missing the following properties:\n", configFileName));
            for (ParamContext missingParameter : missingParameters) {
                message.append(String.format(" '%s' needed by %s\n", missingParameter.getParam().getParamName(), missingParameter.getKey()));
            }
            if (!suppressWarnings)
                throw new MojoFailureException(message.toString());
            else
                getLog().warn(message.toString());

        }

        Set<String> unknownProperties = new TreeSet();
        for (String name : props.stringPropertyNames()) {
            if (!knownProperties.contains(name)) {
                unknownProperties.add(name);
            }
        }

        if (!unknownProperties.isEmpty()) {
            StringBuilder message = new StringBuilder(String.format("Config file %s contains the following unused properties:\n", configFileName));
            for (String unknownProperty : unknownProperties) {
                message.append(String.format("'%s'\n", unknownProperty));
            }
            if (!suppressWarnings)
                throw new MojoFailureException(message.toString());
            else
                getLog().warn(message.toString());
        }

        return props;
    }

    private void writeExampleConfig(Map<String, List<PluginConfig>> configs) throws FileNotFoundException, UnsupportedEncodingException {
        try (PrintWriter out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(exampleConfigFile), "iso-8859-1"))) {


            for (Map.Entry<String, List<PluginConfig>> entry : configs.entrySet()) {

                getLog().info("Found plugin " + entry.getKey());

                out.println("############################");
                out.println("# " + entry.getKey());
                out.println("#");
                List<PluginConfig> plugins = entry.getValue();

                for (PluginConfig config : plugins) {


                    getLog().info("\tFound plugin class " + config.getClassName());
                    if (!config.getConfigParams().isEmpty()) {

                        Predicate<PluginConfigParam> hasDefaultValue = PluginConfigParam::hasDefaultValue;


                        config.getConfigParams().stream().filter(hasDefaultValue.negate()).forEach(param -> {
                            getLog().info("\t\tFound plugin param " + param.getParamName());
                            out.println();
                            document(param, out, "# ");
                            out.print(param.getParamName());
                            out.print("=");
                            out.println();

                        });

                        if (config.getConfigParams().stream().filter(hasDefaultValue).findFirst().isPresent()) {
                            out.println();
                            out.println("## Parameters with default values: ");
                            config.getConfigParams().stream().filter(hasDefaultValue).forEach(param -> {
                                getLog().info("\t\tFound plugin param " + param.getParamName());
                                out.println("##");
                                document(param, out, "## ");
                                out.print("## ");
                                out.print(param.getParamName());
                                out.print("=");
                                out.println(param.getDefaultValue());
                            });
                        }
                    }

                }
            }

            out.println();
            out.println();
        }

    }

    private Map<String, List<PluginConfig>> findConfigs(DocumentBuilder context) throws MojoFailureException, MojoExecutionException, IOException {
        Map<String, List<PluginConfig>> configs = new LinkedHashMap<>();
        for (Plugin plugin : getPlugins()) {

            File pluginFile = resolveArtifactFile(plugin.getCoords());

            List<PluginConfig> configsForPlugin = readPluginConfigs(pluginFile, context);


            if (configsForPlugin != null && hasProps(configsForPlugin)) {
                configs.put(plugin.getCoords(), configsForPlugin);
            }
        }
        return configs;
    }

    private void document(PluginConfigParam param, PrintWriter out, String prefix) {
        if (param.hasDocumentation()) {

            for (String line : param.getDoc().split("\n")) {
                out.print(prefix);
                out.print(line);
                out.println(":");
            }
        }
    }

    private boolean hasProps(List<PluginConfig> configs) {
        for (PluginConfig config : configs) {
            if (!config.getConfigParams().isEmpty()) {
                return true;
            }
        }
        return false;
    }

    private List<PluginConfig> readPluginConfigs(File pluginFile, DocumentBuilder documentBuilder) throws IOException {

        List<PluginConfig> configs = new ArrayList<>();
        String path = "META-INF/services/ReststopPlugin/simple.txt";

        if (pluginFile.isDirectory()) {
            File descriptorFile = new File(pluginFile, path);
            if (descriptorFile.exists()) {
                try (BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(descriptorFile)))) {
                    String className;
                    while ((className = br.readLine()) != null) {
                        try (InputStream is = new FileInputStream(new File(pluginFile, className.replace('.', '/') + ".config-params"))) {
                            configs.add(new PluginConfig(className, new ParamsUnmarshaller().unmarshal(is, documentBuilder)));
                        }
                    }
                }
            }
        } else {
            try (JarFile jarFile = new JarFile(pluginFile)) {
                ZipEntry entry = jarFile.getEntry(path);
                if (entry != null) {
                    try (BufferedReader br = new BufferedReader(new InputStreamReader(jarFile.getInputStream(entry)))) {
                        String className;
                        while ((className = br.readLine()) != null) {
                            ZipEntry configParamsEntry = jarFile.getEntry(className.replace('.', '/') + ".config-params");
                            try (InputStream is = jarFile.getInputStream(configParamsEntry)) {
                                configs.add(new PluginConfig(className, new ParamsUnmarshaller().unmarshal(is, documentBuilder)));
                            }
                        }
                    }


                }
            }
        }
        return configs;
    }

    @Override
    protected List<Plugin> getPlugins() {


        List<Plugin> plugins = new ArrayList<>();
        if (mavenProject.getPackaging().equals("jar")) {
            if (hasArtifactFileFromPackagePhase()) {
                Plugin projectPlugin = new Plugin(mavenProject.getGroupId(), mavenProject.getArtifactId(), mavenProject.getVersion());
                projectPlugin.setSourceDirectory(mavenProject.getBasedir());
                plugins.add(projectPlugin);
            }

            {
                Plugin devConsolePlugin = new Plugin("org.kantega.reststop", "reststop-development-console", pluginVersion);
                plugins.add(devConsolePlugin);
            }


            {
                Plugin developmentPlugin = new Plugin("org.kantega.reststop", "reststop-development-plugin", pluginVersion);
                plugins.add(developmentPlugin);
            }
        }

        plugins.addAll(super.getPlugins());
        return plugins;
    }

    private boolean hasArtifactFileFromPackagePhase() {
        return mavenProject.getArtifact() != null && mavenProject.getArtifact().getFile() != null && mavenProject.getArtifact().getFile().exists();
    }

    private class ParamContext {
        private final String key;
        private final PluginConfig config;
        private final PluginConfigParam param;

        public ParamContext(String key, PluginConfig config, PluginConfigParam param) {

            this.key = key;
            this.config = config;
            this.param = param;
        }

        public String getKey() {
            return key;
        }

        public PluginConfig getConfig() {
            return config;
        }

        public PluginConfigParam getParam() {
            return param;
        }
    }
}
