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
import org.kantega.reststop.api.FilterPhase;
import org.kantega.reststop.api.PluginListener;
import org.kantega.reststop.api.Reststop;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.servlet.ServletContext;
import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.util.Arrays.asList;

/**
 *
 */
public class DevelopmentPlugin extends DefaultReststopPlugin {
    private boolean pluginManagerStarted;

    public DevelopmentPlugin(final Reststop reststop, ServletContext servletContext) {

        Document pluginsXml = (Document) servletContext.getAttribute("pluginsXml");


        if(! loadedByDevelopmentClassLoader()) {

            for(PluginInfo info : PluginInfo.parse(pluginsXml)) {

                if(info.isDevelopmentPlugin()) {
                    File sourceDirectory  = info.getSourceDirectory();
                    if(sourceDirectory != null) {

                        ClassLoader pluginParentClassLoader = reststop.getPluginParentClassLoader();
                        final DevelopmentClassloader devloader = createClassLoader(info, pluginParentClassLoader);

                        addPluginListener(new PluginListener() {
                            @Override
                            public void pluginManagerStarted() {
                                pluginManagerStarted = true;
                                reststop.changePluginClassLoaders().remove(getClass().getClassLoader()).add(devloader).commit();
                            }
                        });

                        return;
                    }

                }
            }
        }

        final DevelopmentClassLoaderProvider provider = new DevelopmentClassLoaderProvider();


        for (PluginInfo info : PluginInfo.parse(pluginsXml)) {
            if(info.isDevelopmentPlugin()) {
                provider.addExistingClassLoader(info.getPluginId(), createClassLoader(info, reststop.getPluginParentClassLoader()));
            }

            if(!info.isDirectDeploy()) {
                provider.addPluginInfo(info);
            }

        }

        

        if(!loadedByDevelopmentClassLoader()) {
            addPluginListener(new PluginListener() {
                @Override
                public void pluginManagerStarted() {
                    provider.start(reststop);
                }
            });
        } else {
            provider.start(reststop);
        }

        addServletFilter(reststop.createFilter(new RedeployFilter(provider, reststop), "/*", FilterPhase.UNMARSHAL));

    }

    private DevelopmentClassloader createClassLoader(PluginInfo info, ClassLoader pluginParentClassLoader) {
        List<File> runtime  = info.getClassPath("runtime");
        List<File> compile  = info.getClassPath("compile");
        List<File> test  = info.getClassPath("test");

        return new DevelopmentClassloader(info.getSourceDirectory(), compile, runtime, test, pluginParentClassLoader);
    }

    private boolean loadedByDevelopmentClassLoader() {
        return getClass().getClassLoader().getClass().getName().equals(DevelopmentClassloader.class.getName());
    }

    private List<File> parseClasspath(String classPath) {
        List<File> files = new ArrayList<>();
        for(String path : classPath.split(File.pathSeparator)) {
            File file = new File(path);
            files.add(file);
        }
        return files;
    }

    public static class PluginInfo {

        private Map<String, List<File>> classpaths = new HashMap<>();
        private String groupId;
        private String artifactId;
        private String version;
        private File pluginFile;
        private File sourceDirectory;
        private boolean directDeploy;

        public List<File> getClassPath(String scope) {
            if(!classpaths.containsKey(scope)) {
                classpaths.put(scope, new ArrayList<File>());
            }

            return classpaths.get(scope);
        }

        static List<PluginInfo> parse(Document document) {
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
                        Element artifact = (Element) artifacts.item(a);
                        pluginInfo.getClassPath(scope).add(new File(artifact.getAttribute("file")));

                    }


                }
            }
            return infos;
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
            return getGroupId()+":" + getArtifactId()+":" + getVersion();
        }

        public void setDirectDeploy(boolean directDeploy) {
            this.directDeploy = directDeploy;
        }

        public boolean isDirectDeploy() {
            return directDeploy;
        }
    }
}
