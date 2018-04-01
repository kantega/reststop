package org.kantega.reststop.bootstrap;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class BootstrapHelper {

    public static ClassLoader createClassLoader(List<URL> commonUrls, ClassLoader parentClassLoader) throws MalformedURLException {

        return new URLClassLoader(commonUrls.toArray(new URL[commonUrls.size()]), parentClassLoader);
    }

    public static List<URL> getCommonURLs(Document pluginsXml, File repositoryDirectory) throws MalformedURLException {
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
