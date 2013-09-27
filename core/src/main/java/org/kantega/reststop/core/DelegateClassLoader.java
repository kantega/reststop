package org.kantega.reststop.core;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;

/**
 *
 */
public class DelegateClassLoader extends ClassLoader {
    private final Set<ClassLoader> delegates;

    public DelegateClassLoader(ClassLoader parentClassLoader, Set<ClassLoader> delegates) {
        super(parentClassLoader);
        this.delegates = delegates;
    }


    @Override
    public Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        try {
            return getParent().loadClass(name);
        } catch (ClassNotFoundException e) {

            for (ClassLoader delegate : delegates) {
                try {
                    return delegate.loadClass(name);
                } catch (ClassNotFoundException e1) {

                }

            }
            throw new ClassNotFoundException(name);
        }
    }

    @Override
    protected URL findResource(String name) {
        URL resource = getParent().getResource(name);
        if (resource != null) {
            return resource;
        }
        for (ClassLoader delegate : delegates) {
            resource = delegate.getResource(name);
            if (resource != null) {
                return resource;
            }
        }
        return null;
    }

    @Override
    protected Enumeration<URL> findResources(String name) throws IOException {
        Enumeration<URL> parentResources = getParent().getResources(name);
        List<URL> delegateResources = null;
        for (ClassLoader delegate : delegates) {
            Enumeration<URL> resources = delegate.getResources(name);
            if(resources != null ) {
                if(delegateResources == null) {
                    delegateResources = new LinkedList<URL>();
                }
                delegateResources.addAll(Collections.list(resources));
            }

        }
        if(delegateResources == null) {
            return parentResources;
        } else {
            LinkedList<URL> urls = new LinkedList<URL>();
            if(parentResources != null) {
                urls.addAll(Collections.list(parentResources));
            }
            urls.addAll(delegateResources);
            return Collections.enumeration(urls);
        }
    }


}
