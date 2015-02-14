package org.kantega.reststop.deploy;

import org.eclipse.aether.util.artifact.JavaScopes;
import org.kantega.reststop.api.Reststop;
import org.kantega.reststop.classloaderutils.Artifact;
import org.kantega.reststop.classloaderutils.PluginClassLoader;
import org.kantega.reststop.classloaderutils.PluginInfo;
import org.kantega.reststop.deploy.model.Plugin;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 *
 */
public class DeployService {
    private final File localMavenRepository;
    private final Reststop reststop;
    private Map<String, PluginClassLoader> started = new HashMap<>();

    public DeployService(File localMavenRepository, Reststop reststop) {
        this.localMavenRepository = localMavenRepository;
        this.reststop = reststop;
    }

    public synchronized Map<String, Plugin> deployedPlugins() {

        Map<String, Plugin> plugins = new TreeMap<>();

        try {
            if(Files.exists(descriptor())) {
                for (String gav : Files.readAllLines(descriptor())) {
                    Plugin plugin = new Plugin(gav);
                    plugins.put(plugin.getGroupId() +":" + plugin.getArtifactId(), plugin);
                }

            }
            return plugins;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Path descriptor() {
        return localMavenRepository.toPath().resolve("reststop-plugins.txt");
    }

    public synchronized void stop(Plugin plugin) {
        removeExistingPlugin(plugin.getGroupId()+":"+ plugin.getArtifactId());
    }

    public synchronized void start(PluginInfo pluginInfo) {

        removeExistingPlugin(pluginInfo.getGroupIdAndArtifactId());

        try {
            List<URL> urls = new ArrayList<>();

            for (Artifact artifact : pluginInfo.getClassPath(JavaScopes.RUNTIME)) {
                urls.add(artifact.getFile().toURI().toURL());
            }

            PluginClassLoader pluginClassLoader = new PluginClassLoader(pluginInfo, urls.toArray(new URL[urls.size()]), reststop.getPluginParentClassLoader());

            reststop.changePluginClassLoaders().add(pluginClassLoader).commit();

            started.put(pluginInfo.getGroupIdAndArtifactId(), pluginClassLoader);
        } catch (MalformedURLException e) {
            throw new RuntimeException(e);
        }
    }

    private synchronized void removeExistingPlugin(String groupIdAndArtifactId) {
        PluginClassLoader existing = started.get(groupIdAndArtifactId);
        if(existing != null) {
            reststop.changePluginClassLoaders().remove(existing).commit();
            started.remove(groupIdAndArtifactId);
            try {
                existing.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public synchronized void deploy(PluginInfo pluginInfo) {
        start(pluginInfo);
        Map<String, Plugin> plugins = deployedPlugins();
        plugins.put(pluginInfo.getGroupIdAndArtifactId(), new Plugin(pluginInfo.getGroupId(), pluginInfo.getArtifactId(), pluginInfo.getVersion()));
        savePluginDescriptor(plugins);
    }

    private void savePluginDescriptor(Map<String, Plugin> plugins) {
        List<String> lines = new ArrayList<>();
        for (Plugin plugin : plugins.values()) {
            lines.add(plugin.getCoords());
        }
        try {
            Files.write(descriptor(), lines, Charset.forName("utf-8"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    public synchronized void undeploy(Plugin plugin) {
        String groupIdAndArtifactId = plugin.getGroupId() + ":" + plugin.getArtifactId();
        removeExistingPlugin(groupIdAndArtifactId);
        Map<String, Plugin> plugins = deployedPlugins();
        plugins.remove(groupIdAndArtifactId);
        savePluginDescriptor(plugins);
    }
}
