package org.kantega.reststop.api.jaxrs;

import org.kantega.reststop.api.ReststopPlugin;

import javax.ws.rs.core.Application;
import java.util.Collection;

/**
 *
 */
public interface JaxRsPlugin extends ReststopPlugin {
    Collection<Application> getJaxRsApplications();
}
