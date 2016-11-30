package org.kantega.reststop.servlet.deploy;

import org.kantega.reststop.api.Plugin;
import org.kantega.reststop.api.PluginExport;
import org.kantega.reststop.servlet.api.ServletDeployer;

import javax.servlet.Filter;
import java.util.Collection;

/**
 *
 */
@Plugin
public class ServletDeployPlugin {

    public ServletDeployPlugin(ServletDeployer servletDeployer, Collection<PluginExport<Filter>> filters) {
        servletDeployer.deploy(filters);
    }
}
