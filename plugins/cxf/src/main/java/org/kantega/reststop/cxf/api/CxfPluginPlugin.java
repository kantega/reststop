package org.kantega.reststop.cxf.api;

import org.kantega.reststop.api.DefaultReststopPlugin;
import org.kantega.reststop.api.ReststopPlugin;

import javax.xml.ws.Endpoint;

/**
 *
 */
public interface CxfPluginPlugin extends ReststopPlugin {
    void customizeEndpoint(Endpoint endpoint);
}
