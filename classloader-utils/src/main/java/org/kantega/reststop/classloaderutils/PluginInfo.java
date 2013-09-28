package org.kantega.reststop.classloaderutils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.util.*;

import static java.util.Arrays.asList;

/**
 *
 */
public class PluginInfo {

    private Map<String, List<Artifact>> classpaths = new HashMap<>();
    private String groupId;
    private String artifactId;
    private String version;
    private File pluginFile;
    private File sourceDirectory;
    private boolean directDeploy;

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

            pluginInfo.setGroupId(pluginElement.getAttribute("groupId"));
            pluginInfo.setArtifactId(pluginElement.getAttribute("artifactId"));
            pluginInfo.setVersion(pluginElement.getAttribute("version"));
            pluginInfo.setDirectDeploy("true".equals(pluginElement.getAttribute("directDeploy")));
            File pluginJar = new File(pluginElement.getAttribute("pluginFile"));
            pluginInfo.setPluginFile(pluginJar);
            String sourceDir = pluginElement.getAttribute("sourceDirectory");
            if (sourceDir != null && !sourceDir.trim().isEmpty()) {
                pluginInfo.setSourceDirectory(new File(sourceDir));
            }


            for (String scope : asList("test", "runtime", "compile")) {

                Element runtimeElement = (Element) pluginElement.getElementsByTagName(scope).item(0);


                NodeList artifacts = runtimeElement.getElementsByTagName("artifact");


                for (int a = 0; a < artifacts.getLength(); a++) {
                    Element artifactElement = (Element) artifacts.item(a);
                    File file = new File(artifactElement.getAttribute("file"));

                    Artifact artifact = new Artifact(artifactElement.getAttribute("groupId"),
                            artifactElement.getAttribute("artifactId"),
                            artifactElement.getAttribute("version"), file);

                    pluginInfo.getClassPath(scope).add(artifact);

                }


            }
        }
        return infos;
    }

    public List<PluginInfo> getDependsOn(Collection<PluginInfo> all) {
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

    @Override
    public String toString() {
        return "Plugin " + getGroupId() +":" + getArtifactId() +":" + getVersion();
    }

    public void setGroupId(String groupId) {
        this.groupId = groupId;
    }

    public String getGroupId() {
        return groupId;
    }

    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    public String getArtifactId() {
        return artifactId;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }

    public void setPluginFile(File pluginFile) {
        this.pluginFile = pluginFile;
    }

    public File getPluginFile() {
        return pluginFile;
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


    public static List<PluginInfo> sortByRuntimeDependencies(List<PluginInfo> infos) {
        Map<String, Boolean> colors = new HashMap<String, Boolean>();
        List<PluginInfo> sorted = new LinkedList<PluginInfo>();

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

    private static void dfs(PluginInfo info, Map<String, PluginInfo> plugins, Map<String, Boolean> colors, List<PluginInfo> sorted) {
        colors.put(info.getGroupIdAndArtifactId(), Boolean.FALSE);
        for (Artifact dep : info.getClassPath("compile")) {
            String key = dep.getGroupIdAndArtifactId();
            if (plugins.containsKey(key) && !colors.containsKey(key)) {
                dfs(plugins.get(key), plugins, colors, sorted);
            }
        }
        colors.put(info.getGroupIdAndArtifactId(), Boolean.TRUE);
        sorted.add(info);
    }

    public String getGroupIdAndArtifactId() {
        return getGroupId() +":" + getArtifactId();
    }
}
