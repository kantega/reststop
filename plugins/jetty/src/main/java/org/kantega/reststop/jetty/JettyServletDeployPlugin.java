package org.kantega.reststop.jetty;

import org.kantega.reststop.api.Plugin;
import org.kantega.reststop.api.PluginExport;
import org.kantega.reststop.servlet.api.ServletDeployer;

import javax.servlet.Filter;
import java.util.Collection;

/**
 *
 */
@Plugin
public class JettyServletDeployPlugin {

    public JettyServletDeployPlugin(ServletDeployer servletDeployer, Collection<PluginExport<Filter>> filters) {
        servletDeployer.deploy(filters);
    }
}
