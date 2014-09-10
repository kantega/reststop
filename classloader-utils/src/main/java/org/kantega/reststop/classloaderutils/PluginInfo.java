package org.kantega.reststop.classloaderutils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.*;
import java.util.*;
import java.util.jar.JarFile;
import java.util.zip.ZipEntry;

import static java.util.Arrays.asList;

/**
 *
 */
public class PluginInfo extends Artifact {

    private Map<String, List<Artifact>> classpaths = new HashMap<>();
    private File sourceDirectory;
    private boolean directDeploy;
    private List<Artifact> dependsOn = new ArrayList<>();
    private List<Artifact> importsFrom = new ArrayList<>();
    private Properties config = new Properties();
    private Set<String> imports, exports;
    private Integer priority = null;

    public List<Artifact> getClassPath(String scope) {
        if (!classpaths.containsKey(scope)) {
            classpaths.put(scope, new ArrayList<Artifact>());
        }

        return classpaths.get(scope);
    }

    public List<File> getClassPathFiles(String scope) {
        List<File> files = new ArrayList<>();
        for (Artifact artifact : getClassPath(scope)) {
            files.add(artifact.getFile());
        }
        return files;
    }

    public static List<PluginInfo> parse(Document document) {
        List<PluginInfo> infos = new ArrayList<>();

        NodeList pluginElements = document.getDocumentElement().getElementsByTagName("plugin");

        for (int i = 0; i < pluginElements.getLength(); i++) {

            PluginInfo pluginInfo = new PluginInfo();

            infos.add(pluginInfo);

            Element pluginElement = (Element) pluginElements.item(i);

            parseGav(pluginInfo, pluginElement);

            pluginInfo.setDirectDeploy(!"false".equals(pluginElement.getAttribute("directDeploy")));
            String pluginFile = pluginElement.getAttribute("pluginFile");
            if(!pluginFile.isEmpty()) {
                File pluginJar = new File(pluginFile);
                pluginInfo.setFile(pluginJar);
            }
            String sourceDir = pluginElement.getAttribute("sourceDirectory");
            if(sourceDir != null && !sourceDir.trim().isEmpty()) {
                pluginInfo.setSourceDirectory(new File(sourceDir));
            }

            NodeList dependsOnElems = pluginElement.getElementsByTagName("depends-on");
            for(int d = 0; d < dependsOnElems.getLength(); d++) {
                Element depElem = (Element) dependsOnElems.item(d);
                Artifact depArt = new Artifact();
                parseGav(depArt, depElem);
                pluginInfo.addDependsOn(depArt);
            }

            NodeList importsFromElems = pluginElement.getElementsByTagName("imports-from");
            for(int d = 0; d < importsFromElems.getLength(); d++) {
                Element importElem = (Element) importsFromElems.item(d);
                Artifact depArt = new Artifact();
                parseGav(depArt, importElem);
                pluginInfo.addImportsFrom(depArt);
            }

            NodeList configElems = pluginElement.getElementsByTagName("config");

            Properties props = new Properties();
            for(int c = 0; c < configElems.getLength(); c++) {
                Element configElem = (Element) configElems.item(c);

                NodeList propElems = configElem.getElementsByTagName("prop");
                for(int p = 0; p < configElems.getLength(); p++) {
                    Element propElem = (Element) propElems.item(p);

                    props.setProperty(propElem.getAttribute("name"), propElem.getAttribute("value"));
                }

            }

            pluginInfo.setConfig(props);

            for (String scope : asList("test", "runtime", "compile")) {

                NodeList classPathElems = pluginElement.getElementsByTagName(scope);
                if(classPathElems.getLength() > 0) {
                    Element runtimeElement = (Element) classPathElems.item(0);


                    NodeList artifacts = runtimeElement.getElementsByTagName("artifact");


                    for (int a = 0; a < artifacts.getLength(); a++) {
                        Element artifactElement = (Element) artifacts.item(a);
                        String filePath = artifactElement.getAttribute("file");
                        File file = null;
                        if(filePath != null) {
                            file = new File(filePath);
                        }
                        Artifact artifact = new Artifact(artifactElement.getAttribute("groupId"),
                                artifactElement.getAttribute("artifactId"),
                                artifactElement.getAttribute("version"), file);

                        pluginInfo.getClassPath(scope).add(artifact);

                    }
                }

            }
        }
        return infos;
    }

    private void addImportsFrom(Artifact depArt) {
        importsFrom.add(depArt);
    }

    public void addDependsOn(Artifact depArt) {
        dependsOn.add(depArt);
    }

    private static void parseGav(Artifact pluginInfo, Element pluginElement) {
        pluginInfo.setGroupId(pluginElement.getAttribute("groupId"));
        pluginInfo.setArtifactId(pluginElement.getAttribute("artifactId"));
        pluginInfo.setVersion(pluginElement.getAttribute("version"));
    }

    public List<PluginInfo> getServiceProviders(List<PluginInfo> pluginInfos) {
        Set<String> myImports = getImports();
        List<PluginInfo> serviceProviders = new ArrayList<>();

        for (PluginInfo pluginInfo : pluginInfos) {
            if( !pluginInfo.getPluginId().equals(getPluginId())) {
                for (String export : pluginInfo.getExports()) {
                    if(myImports.contains(export)) {
                        serviceProviders.add(pluginInfo);
                        break;
                    }
                }
            }

        }

        return serviceProviders;
    }

    private Set<String> getExports() {
        if(exports == null) {
            exports = readLines("META-INF/services/ReststopPlugin/exports.txt");
        }
        return exports;
    }

    private Set<String> getImports() {
        if(imports == null) {
            imports = readLines("META-INF/services/ReststopPlugin/imports.txt");
        }
        return imports;
    }

    private Set<String> readLines(String path) {
        Set<String> lines = new HashSet<>();

        if(this.getFile() == null) {
            return Collections.emptySet();
        }
        try (JarFile jar = new JarFile(this.getFile())) {
            ZipEntry entry = jar.getEntry(path);
            if(entry != null) {
                try ( BufferedReader br = new BufferedReader(new InputStreamReader(jar.getInputStream(entry), "utf-8"))) {
                    String line;
                    while( (line = br.readLine()) != null) {
                        lines.add(line);
                    }
                }
            }

        } catch (IOException e) {
            throw new RuntimeException();
        }

        return lines;
    }

    public List<PluginInfo> getParents(Collection<PluginInfo> all) {
        List<PluginInfo> deps = new ArrayList<>();

        for (Artifact compile : getClassPath("compile")) {
            for (PluginInfo info : all) {
                if(compile.getGroupIdAndArtifactId().equals(info.getGroupIdAndArtifactId())) {
                    deps.add(info);
                }
            }
        }

        return deps;
    }

    public List<PluginInfo> getChildren(Collection<PluginInfo> all) {
        List<PluginInfo> deps = new ArrayList<>();

            for (PluginInfo info : all) {
                for (Artifact compile : info.getClassPath("compile")) {
                    if(compile.getGroupIdAndArtifactId().equals(getGroupIdAndArtifactId())) {
                        deps.add(info);
                    }
                }
            }


        return deps;
    }

    public List<PluginInfo> getServiceConsumers(List<PluginInfo> all) {
        List<PluginInfo> consumers = new ArrayList<>();

        for (PluginInfo info : all) {
            for (PluginInfo provider : info.getServiceProviders(all)) {
                if(provider.getGroupIdAndArtifactId().equals(getGroupIdAndArtifactId())) {
                    consumers.add(info);
                }
            }
        }


        return consumers;
    }

    @Override
    public String toString() {
        return "Plugin " + getGroupId() +":" + getArtifactId() +":" + getVersion();
    }

    public void setSourceDirectory(File sourceDirectory) {
        this.sourceDirectory = sourceDirectory;
    }

    public File getSourceDirectory() {
        return sourceDirectory;
    }

    public boolean isDevelopmentPlugin() {
        return "org.kantega.reststop".equals(getGroupId()) && "reststop-development-plugin".equals(getArtifactId());
    }

    public String getPluginId() {
        return getGroupId() + ":" + getArtifactId() + ":" + getVersion();
    }

    public void setDirectDeploy(boolean directDeploy) {
        this.directDeploy = directDeploy;
    }


    public boolean isDirectDeploy() {
        return directDeploy;
    }

    public static List<PluginInfo> resolveStartupOrder(List<PluginInfo> infos) {

        infos = sortByPriority(new ArrayList<>(infos));

        Map<String, Boolean> colors = new HashMap<>();
        List<PluginInfo> sorted = new LinkedList<>();

        Map<String, PluginInfo> plugins = new HashMap<>();
        for (PluginInfo info : infos) {
            plugins.put(info.getGroupIdAndArtifactId(), info);
        }

        for (PluginInfo info : infos) {
            if (!colors.containsKey(info.getGroupIdAndArtifactId()))
                dfs(info, plugins, colors, sorted);
        }
        return sorted;
    }

    private static List<PluginInfo> sortByPriority(ArrayList<PluginInfo> pluginInfos) {
        Collections.sort(pluginInfos, new Comparator<PluginInfo>() {
            @Override
            public int compare(PluginInfo o1, PluginInfo o2) {
                return o1.getPriority() - o2.getPriority();
            }
        });
        return pluginInfos;
    }

    private static void dfs(PluginInfo info, Map<String, PluginInfo> plugins, Map<String, Boolean> colors, List<PluginInfo> sorted) {
        colors.put(info.getGroupIdAndArtifactId(), Boolean.FALSE);
        for (Artifact dep : info.getStartupDeps()) {
            String key = dep.getGroupIdAndArtifactId();
            if (plugins.containsKey(key) && !colors.containsKey(key)) {
                dfs(plugins.get(key), plugins, colors, sorted);
            }
        }
        colors.put(info.getGroupIdAndArtifactId(), Boolean.TRUE);
        sorted.add(info);
    }

    private List<Artifact> getStartupDeps() {
        Map<String, Artifact> deps = new LinkedHashMap<>();
        for (Artifact artifact : getDependsOn()) {
            deps.put(artifact.getPluginId(), artifact);
        }
        for (Artifact artifact : getImportsFrom()) {
            deps.put(artifact.getPluginId(), artifact);
        }
        return new ArrayList<>(deps.values());
    }

    public String getGroupIdAndArtifactId() {
        return getGroupId() +":" + getArtifactId();
    }

    public List<Artifact> getDependsOn() {
        return dependsOn;
    }

    public void setConfig(Properties config) {
        this.config = cloneProperties(config);
    }

    public Properties getConfig() {
        return cloneProperties(config);
    }

    private static Properties cloneProperties(Properties props) {
        Properties properties = new Properties();
        if(props != null) {
            properties.putAll(props);
        }
        return properties;
    }

    public List<Artifact> getImportsFrom() {
        return importsFrom;
    }

    public synchronized int getPriority() {
        if(priority == null) {
            Set<String> lines = readLines("META-INF/services/ReststopPlugin/priority.txt");
            if(! lines.isEmpty()) {
                priority = Integer.parseInt(lines.iterator().next());
            }
        }

        if(priority == null) {
            priority = 0;
        }
        return priority;
    }

    public static void configure(List<PluginInfo> pluginInfos, String pluginConfigurationDirectory, String applicationName1) {
        String configDirPath = pluginConfigurationDirectory;
        if(configDirPath != null) {
            File configDir = new File(configDirPath);
            String applicationName = applicationName1;
            File globalConfigFile = applicationName != null ? new File(configDir, applicationName +".conf") : null;
            if(configDir.exists()) {
                for (PluginInfo info : pluginInfos) {

                    File artifact = new File(configDir, info.getArtifactId() +".conf");
                    File artifactVersion = new File(configDir, info.getArtifactId() +"-" + info.getVersion() +".properties");

                    Properties properties = new Properties();
                    properties.putAll(info.getConfig());

                    addProperties(properties, globalConfigFile, artifact, artifactVersion);

                    info.setConfig(properties);
                }
            }
        }
    }

    private static void addProperties(Properties properties, File... files) {
        if(files != null) {
            for (File file : files) {
                if(file != null && file.exists()) {
                    Properties prop = new Properties();
                    try(FileInputStream in = new FileInputStream(file)) {
                        prop.load(in);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    properties.putAll(prop);
                }
            }
        }
    }
}
