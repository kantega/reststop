package org.kantega.reststop.bootstrap;

import org.w3c.dom.Document;

import java.io.File;

/**
 *
 */
public interface Bootstrap {


    default void preBootstrap() {}

    default void bootstrap(File globalConfigurationFile, Document pluginsXml, File repositoryDirectory) {}

    default void postBootstrap() {}

    default void shutdown() {};
}
