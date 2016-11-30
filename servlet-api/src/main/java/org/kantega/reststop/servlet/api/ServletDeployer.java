package org.kantega.reststop.servlet.api;

import org.kantega.reststop.api.PluginExport;

import javax.servlet.Filter;
import java.util.Collection;

/**
 *
 */
public interface ServletDeployer {

    void deploy(Collection<PluginExport<Filter>> filters);
}
