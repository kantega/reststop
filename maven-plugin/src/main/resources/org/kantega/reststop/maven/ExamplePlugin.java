package org.kantega.reststop.maven;

import org.kantega.reststop.api.DefaultReststopPlugin;

/**
 *
 */
public class ExamplePlugin extends DefaultReststopPlugin {

    public ExamplePlugin() {
        addJaxRsSingletonResource(new HelloworldResource());
    }

}