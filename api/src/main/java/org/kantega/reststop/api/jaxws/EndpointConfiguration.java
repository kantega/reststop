package org.kantega.reststop.api.jaxws;

/**
 *
 */
public interface EndpointConfiguration {
    Object getImplementor();

    String getPath();
}
