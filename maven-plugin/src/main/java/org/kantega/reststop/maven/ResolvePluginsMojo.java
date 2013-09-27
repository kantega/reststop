package org.kantega.reststop.maven;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.*;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.jetty.server.Server;
import org.w3c.dom.Document;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.Charset;
import java.nio.file.Files;
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

    @Override
    public void execute() throws MojoExecutionException, MojoFailureException {
        try {
            Document xmlDocument = createPluginXmlDocument();

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
        ArrayList<Plugin> plugins = new ArrayList<>(super.getPlugins());

        Plugin developmentPlugin = new Plugin("org.kantega.reststop", "reststop-development-plugin", mavenProject.getVersion());
        plugins.add(developmentPlugin);
        developmentPlugin.setDirectDeploy(true);


        for (Plugin plugin : plugins) {
            plugin.setSourceDirectory(getSourceDirectory(plugin));
        }

        return plugins;
    }
}
