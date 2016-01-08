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

        File globalConfigurationFile =  new File(args[0]);
        File pluginsXmlFile = new File(args[1]);
        File repositoryDirectory = new File(args[2]);

        Document pluginsXml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pluginsXmlFile);

        List<URL> urls = getCommonURLs(pluginsXml, repositoryDirectory);

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
            bootstrap.bootstrap(globalConfigurationFile, pluginsXml, repositoryDirectory);
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
}
