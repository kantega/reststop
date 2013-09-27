package org.kantega.reststop.api.jaxrs;

import org.kantega.reststop.api.ReststopPlugin;

import java.util.Collection;

/**
 *
 */
public interface JaxRsPlugin extends ReststopPlugin {
    Collection<Object> getJaxRsSingletonResources();

    Collection<Class<?>> getJaxRsContainerClasses();

}
