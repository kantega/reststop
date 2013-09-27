package org.kantega.reststop.cxf.api;

import org.kantega.reststop.api.DefaultReststopPlugin;

import javax.xml.ws.Endpoint;

/**
 *
 */
public class DefaultCxfPluginPlugin extends DefaultReststopPlugin implements CxfPluginPlugin {
    @Override
    public void customizeEndpoint(Endpoint endpoint) {

    }
}
