/*
 * Copyright 2015 Kantega AS
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
import org.w3c.dom.Document;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
@Mojo(name = "resolve-plugins",
        defaultPhase = LifecyclePhase.VALIDATE)
public class ResolvePluginsMojo extends AbstractReststopMojo {

    @Parameter(defaultValue = "${project.build.directory}/reststop/plugins.xml")
    private File pluginsXmlFile;

    @Parameter(defaultValue = "false")
    private boolean addDevelopmentPlugins;

    @Parameter
    private List<Plugin> developmentPlugins;

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            Document xmlDocument = createPluginXmlDocument(false);

            pluginsXmlFile.getParentFile().mkdirs();

            Transformer transformer = TransformerFactory.newInstance().newTransformer();
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");

            transformer.transform(new DOMSource(xmlDocument), new StreamResult(pluginsXmlFile));
        } catch (TransformerException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    @Override
    protected List<Plugin> getPlugins() {
        List<Plugin> plugins = new ArrayList<>();

        if(developmentPlugins != null) {
            for (Plugin plugin : developmentPlugins) {
                plugin.setDirectDeploy(false);
                plugins.add(plugin);
            }
        }

        plugins.addAll(super.getPlugins());

        if(addDevelopmentPlugins) {
            addDevelopmentPlugins(plugins);
        }

        return plugins;
    }
}
