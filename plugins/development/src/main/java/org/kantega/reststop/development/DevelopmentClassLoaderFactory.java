package org.kantega.reststop.development;

import org.kantega.reststop.classloaderutils.Artifact;
import org.kantega.reststop.classloaderutils.PluginClassLoader;
import org.kantega.reststop.classloaderutils.PluginInfo;
import org.kantega.reststop.core2.ClassLoaderFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class DevelopmentClassLoaderFactory implements ClassLoaderFactory {

    private static DevelopmentClassLoaderFactory instance;

    public static DevelopmentClassLoaderFactory getInstance() {
        return instance;
    }

    public DevelopmentClassLoaderFactory() {
        instance = this;
    }

    @Override
    public PluginClassLoader createPluginClassLoader(PluginInfo pluginInfo, ClassLoader parentClassLoader, List<PluginInfo> allPlugins) {

        List<File> runtime = getClasspath(pluginInfo, allPlugins, "runtime", true);
        List<File> compile = getClasspath(pluginInfo, allPlugins,  "compile", false);
        List<File> test = getClasspath(pluginInfo, allPlugins,  "test", false);
        File sourceDir = pluginInfo.getSourceDirectory();

        DevelopmentClassloader classloader = new DevelopmentClassloader(pluginInfo, sourceDir, pluginInfo.getFile(),
                compile,
                runtime,
                test,
                parentClassLoader, 1);

        return classloader;
    }

    private List<File> getClasspath(PluginInfo pluginInfo, List<PluginInfo> allPlugins, String scope, boolean filterParentPlugins) {
        List<File> files = new ArrayList<>();
        for (Artifact artifact : pluginInfo.getClassPath(scope)) {
            if(!filterParentPlugins || allPlugins.stream().noneMatch(p -> p.getPluginId().equals(artifact.getPluginId()))) {
                files.add(artifact.getFile());
            }
        }
        return files;
    }
}
