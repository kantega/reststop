package org.kantega.reststop.api.jaxrs;

import org.kantega.reststop.api.DefaultReststopPlugin;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 *
 */
public class DefaultJaxRsPlugin extends DefaultReststopPlugin implements JaxRsPlugin {
    private final List<Object> jaxRsSingletonResources = new ArrayList<>();
    private final List<Class<?>> jaxRsContainerClasses = new ArrayList<>();

    @Override
    public Collection<Object> getJaxRsSingletonResources() {
        return jaxRsSingletonResources;
    }

    protected void addJaxRsSingletonResource(Object resource) {
        jaxRsSingletonResources.add(resource);
    }

    @Override
    public Collection<Class<?>> getJaxRsContainerClasses() {
        return jaxRsContainerClasses;
    }

    protected void addJaxRsContainerClass(Class<?> clazz) {
        jaxRsContainerClasses.add(clazz);
    }
}
