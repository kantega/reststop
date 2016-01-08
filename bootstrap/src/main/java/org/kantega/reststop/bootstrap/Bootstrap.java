package org.kantega.reststop.bootstrap;

import org.w3c.dom.Document;

import java.io.File;

/**
 *
 */
public interface Bootstrap {


    void bootstrap(File globalConfigurationFile, Document pluginsXml, File repositoryDirectory);

    void shutdown();
}
