package org.kantega.reststop.jaxwsapi;

/**
 *
 */
public interface EndpointConfigurationBuilder {
    Build service(Object service);



    interface Build {
        Build path(String path);

        EndpointConfiguration build();
    }
}
