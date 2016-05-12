/*
 * Copyright 2016 Kantega AS
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

package org.kantega.reststop.bootstrap;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;

/**
 *
 */
public class Main {

    private static List<Bootstrap> bootstraps = new ArrayList<>();

    public static void main(String[] args) throws ParserConfigurationException, IOException, SAXException {

        Settings settings = parseCli(args);

        if(WindowsServiceInstaller.shouldInstallOrUninstall(args)) {
            WindowsServiceInstaller.installOrUninstallAndExit(args, settings);
        }

        Document pluginsXml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(settings.pluginsXmlFile);

        List<URL> urls = getCommonURLs(pluginsXml, settings.repositoryDirectory);

        ClassLoader classLoader = createClassLoader(urls);

        ServiceLoader<Bootstrap> load = ServiceLoader.load(Bootstrap.class, classLoader);
        Iterator<Bootstrap> iterator = load.iterator();
        if(! iterator.hasNext()) {
            throw new IllegalStateException("Could not find any service instance of " + Bootstrap.class +" in class path " + urls);
        }

        for(Bootstrap bootstrap : load) {
            bootstraps.add(bootstrap);
        }

        for (Bootstrap bootstrap : load) {
            bootstrap.preBootstrap();
        }
        for (Bootstrap bootstrap : load) {
            bootstrap.bootstrap(settings.globalConfigurationFile, pluginsXml, settings.repositoryDirectory);
        }
        for (Bootstrap bootstrap : load) {
            bootstrap.postBootstrap();
        }

        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                Collections.reverse(bootstraps);
                for (Bootstrap bootstrap : bootstraps) {
                    bootstrap.shutdown();
                }
            }
        });

    }

    /**
     * Required by {@link WindowsServiceInstaller}.
     *
     * @param args not used
     */
    @SuppressWarnings("unused")
    public static void shutdown(String args[]) {
        Runtime.getRuntime().exit(0);
    }

    private static Settings parseCli(String[] args) {
        String configFilePath = null;
        String repositoryPath = "repository";
        String pluginsXmlPath = "plugins.xml";

        for (int i = 0; i < args.length; i++) {
            String arg = args[i].trim();
            if("--config".equals(arg)) {
                if(i == args.length-1) {
                    usage("--config option requires a path");
                }
                configFilePath = args[i+1];
                i++;
            } else if("--repository".equals(arg)) {
                if(i == args.length-1) {
                    usage("--repository option requires a path");
                }
                repositoryPath = args[i+1];
                i++;
            } else if("--plugins".equals(arg)) {
                if(i == args.length-1) {
                    usage("--plugins option requires a path");
                }
                pluginsXmlPath = args[i+1];
                i++;
            } else if(WindowsServiceInstaller.isOption(arg)) {
                i = i + WindowsServiceInstaller.getOptionParameterCount(arg);
            } else if(!arg.startsWith("--")) {
                usage("'" +arg + "' is not an option");
            } else {
                usage("Unknown option: " + arg);
            }
        }

        if(configFilePath == null) {
            usage("--config option is required");
        }

        File configFile = new File(configFilePath);
        File repositoryDirectory = new File(repositoryPath);
        File pluginsXmlFile = new File(pluginsXmlPath);

        if(!configFile.exists()) {
            usage("--config file does not exist: '" + configFilePath +"'");
        }
        if(!repositoryDirectory.exists() || !repositoryDirectory.isDirectory()) {
            usage("--repository directory does not exist: '" + repositoryPath +"'");
        }
        if(! pluginsXmlFile.exists()) {
            usage("--plugins xml file does not exist: '" + pluginsXmlPath +"'");
        }

        return new Settings(configFile, pluginsXmlFile, repositoryDirectory);
    }

    private static void usage(String message) {
        System.out.println("ERROR: " + message);
        System.out.println();
        System.out.println("Usage: java -jar bootstrap.jar --config <configFile> [options]");
        System.out.println();
        System.out.println("Options:");
        System.out.println("\t--repository <directory>   (Default: 'repository/' in working directory)");
        System.out.println("\t--plugins <pluginsXmlFile> (Default: 'plugins.xml' in working directory)");
        System.out.println(WindowsServiceInstaller.getOptions());
        System.exit(0);
    }

    private static ClassLoader createClassLoader(List<URL> commonUrls) throws MalformedURLException {

        return new URLClassLoader(commonUrls.toArray(new URL[commonUrls.size()]), Main.class.getClassLoader());
    }

    private static List<URL> getCommonURLs(Document pluginsXml, File repositoryDirectory) throws MalformedURLException {
        NodeList commonElems = pluginsXml.getDocumentElement().getElementsByTagName("common");

        List<URL> urls = new ArrayList<>();

        for(int i = 0; i < commonElems.getLength(); i++) {
            Element commonElem = (Element) commonElems.item(i);
            String groupId = commonElem.getAttribute("groupId");
            String artifactId = commonElem.getAttribute("artifactId");
            String version = commonElem.getAttribute("version");

            urls.add(getFile(repositoryDirectory, groupId, artifactId, version).toURI().toURL());
        }
        return urls;
    }

    private static File getFile(File repoDir, String groupId, String artifactId, String version) {

        return new File(repoDir,
                groupId.replace('.', '/') + "/"
                        + artifactId + "/"
                        + version + "/"
                        + artifactId + "-" + version + ".jar");

    }

    static class Settings {

        final File globalConfigurationFile;
        final File pluginsXmlFile;
        final File repositoryDirectory;

        public Settings(File globalConfigurationFile, File pluginsXmlFile, File repositoryDirectory) {
            this.globalConfigurationFile = globalConfigurationFile;
            this.pluginsXmlFile = pluginsXmlFile;
            this.repositoryDirectory = repositoryDirectory;
        }

        public List<String> getAsList() {
            return Arrays.asList(
                    "--config " + globalConfigurationFile.getAbsolutePath(),
                    "--repository " + repositoryDirectory.getAbsolutePath(),
                    "--plugins " + pluginsXmlFile.getAbsolutePath());
        }
    }
}
