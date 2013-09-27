package org.kantega.reststop.cxf;

import org.apache.cxf.Bus;
import org.apache.cxf.resource.ResourceManager;
import org.apache.cxf.resource.ResourceResolver;

import java.io.InputStream;
import java.net.URL;

/**
 *
 */
public class ContextClassLoaderResourceResolver implements ResourceResolver{

    public ContextClassLoaderResourceResolver(Bus bus) {
        bus.getExtension(ResourceManager.class).addResourceResolver(this);
    }

    public <T> T resolve(String resourceName, Class<T> resourceType) {
        if (resourceName == null || resourceType == null) {
            return null;
        }
        if(!resourceType.isAssignableFrom(URL.class)) {
            return null;
        }
        ClassLoader classLoader = CxfReststopPlugin.pluginClassLoader.get();
        if(classLoader == null) {
            return null;
        }
        URL url = classLoader.getResource(resourceName);
        if (resourceType.isInstance(url)) {
            return resourceType.cast(url);
        }
        return null;
    }

    public InputStream getAsStream(String name) {
        ClassLoader classLoader = CxfReststopPlugin.pluginClassLoader.get();
        if(classLoader == null) {
            return null;
        }
        return classLoader.getResourceAsStream(name);
    }
}
