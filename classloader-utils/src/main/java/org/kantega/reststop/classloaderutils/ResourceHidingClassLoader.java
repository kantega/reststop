package org.kantega.reststop.classloaderutils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;

/**
 *
 */
public class ResourceHidingClassLoader extends ClassLoader {

    private final String[] localResourcePrefixes;

    /**
     * Creates a ResourceHidingClassLoader hiding resources in <code>META-INF/services/PluginName/</code> and
     * <code>META-INF/services/com.example.PluginName/</code>.
     *
     * @param parent      the parent class loader
     * @param pluginClass the plugin class to hide resources for.
     */
    public ResourceHidingClassLoader(ClassLoader parent, Class pluginClass) {
        super(parent);
        localResourcePrefixes = new String[]{"META-INF/services/" + pluginClass.getSimpleName() + "/",
                "META-INF/services/" + pluginClass.getName() + "/"};
    }


    @Override
    public InputStream getResourceAsStream(String name) {
        final URL resource = getResource(name);
        try {
            return resource == null ? null : resource.openStream();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        return getParent().loadClass(name);
    }

    @Override
    public URL getResource(String name) {
        if (isLocalResource(name)) {
            return super.findResource(name);
        } else {
            return super.getResource(name);
        }
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if (isLocalResource(name)) {
            return super.findResources(name);
        } else {
            return super.getResources(name);
        }
    }

    protected boolean isLocalResource(String name) {
        for (String localResourcePrefix : localResourcePrefixes) {
            if (name.startsWith(localResourcePrefix)) {
                return true;
            }
        }
        return false;
    }
}

