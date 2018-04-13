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

import org.apache.commons.io.FileUtils;
import org.w3c.dom.*;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.File;
import java.io.IOException;

public class MavenPomUtils {

    protected void addModule(final File source, final File destination, final String moduleName) throws IOException, SAXException, ParserConfigurationException, TransformerException {

        final Boolean keepDeclaration = checkForKeepDeclaration(source);
        Document doc = getDocumentFromFile(source);

        Node module = getNodeByName(doc, "modules");
        module.appendChild(createElementWithValue(doc, "module", moduleName));

        saveDocumentToFile(destination, doc, keepDeclaration);
    }

    private Boolean checkForKeepDeclaration(File source) throws IOException {
        String xml = FileUtils.readFileToString(source);
        return xml.startsWith("<?xml");
    }

    protected void updateVersionInPom(final File source, final File destination, final String updateVersion) throws ParserConfigurationException, SAXException, IOException, TransformerException {
        final Boolean keepDeclaration = checkForKeepDeclaration(source);
        Document doc = getDocumentFromFile(source);

        Node version = getVersionNode(doc);
        version.setTextContent(updateVersion);

        saveDocumentToFile(destination, doc, keepDeclaration);
    }

    private void saveDocumentToFile(File destination, Document doc, Boolean keepDeclaration) throws TransformerException {
        TransformerFactory transformerFactory = TransformerFactory.newInstance();
        Transformer transformer = transformerFactory.newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
        if (keepDeclaration) {
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        }
        DOMSource dom = new DOMSource(doc);
        StreamResult result = new StreamResult(destination);
        transformer.transform(dom, result);
    }

    private Document getDocumentFromFile(File source) throws ParserConfigurationException, SAXException, IOException {
        DocumentBuilderFactory docFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder docBuilder = docFactory.newDocumentBuilder();
        Document doc = docBuilder.parse(source);
        doc.setXmlStandalone(true);
        return doc;
    }

    private Node getNodeByName(final Document doc, String name) {

        NodeList nodes = doc.getElementsByTagName(name);
        for (int i = 0; i < nodes.getLength(); i++) {
            Node node = nodes.item(i);
            if (node.getParentNode().getNodeName().compareToIgnoreCase("project") == 0)
                return node;
        }

        throw new RuntimeException(String.format("Unsupported. Please add <%s> tag to your pom.xml.", name));

    }

    private Node getVersionNode(final Document doc) {

        NodeList nodes = doc.getElementsByTagName("version");
        for (int i = 0; i < nodes.getLength(); i++) {
            Node versionNode = nodes.item(i);
            if (versionNode.getParentNode().getNodeName().compareToIgnoreCase("project") == 0)
                return versionNode;
        }

        throw new RuntimeException(String.format("Unsupported. Please add <version> tag to your %s:%s pom.xml.", doc.getElementsByTagName("groupId"), doc.getElementsByTagName("artifactId")));
    }


    public void addPluginToReststop(File source, File destination, String groupdId, String artifactId, String version) throws IOException, ParserConfigurationException, SAXException, TransformerException, XPathExpressionException {
        final Boolean keepDeclaration = checkForKeepDeclaration(source);
        Document doc = getDocumentFromFile(source);

        XPath xPath = XPathFactory.newInstance().newXPath();
        NodeList nodes = (NodeList) xPath.evaluate("/project/build/plugins/plugin/artifactId[text()='reststop-maven-plugin']", doc.getDocumentElement(), XPathConstants.NODESET);
        Element e = null;
        for (int i = 0; i < nodes.getLength(); ++i) {
            e = (Element) nodes.item(i);
        }

        Node conf;
        if (e != null) {
            conf = getNodeByName(doc, e.getParentNode(), "configuration");
            Node confPlugins = getNodeByName(doc, conf, "plugins");

            Element p = doc.createElement("plugin");

            p.appendChild(createElementWithValue(doc, "groupId", groupdId));
            p.appendChild(createElementWithValue(doc, "artifactId", artifactId));
            p.appendChild(createElementWithValue(doc, "version", version));

            confPlugins.appendChild(p);

            saveDocumentToFile(destination, doc, keepDeclaration);
        }

    }

    private Element createElementWithValue(Document doc, String nodeName, String value) {
        Text text = doc.createTextNode(value);
        Element e = doc.createElement(nodeName);
        e.appendChild(text);
        return e;
    }

    private Node getNodeByName(Document doc, Node node, String nodeName) {
        NodeList nodes = node.getChildNodes();
        for (int i = 0; i < nodes.getLength(); i++) {
            Node n = nodes.item(i);
            if (n.getNodeName().compareToIgnoreCase(nodeName) == 0)
                return n;
        }
        throw new RuntimeException(String.format("Could not find %s tag in your %s:%s pom.xml.", nodeName, doc.getElementsByTagName("groupId"), doc.getElementsByTagName("artifactId")));
    }
}
