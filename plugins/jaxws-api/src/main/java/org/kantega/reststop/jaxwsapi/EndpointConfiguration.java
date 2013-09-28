package org.kantega.reststop.jaxwsapi;

/**
 *
 */
public interface EndpointConfiguration {
    Object getImplementor();

    String getPath();
}
