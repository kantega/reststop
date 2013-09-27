package org.kantega.reststop.custom.api;

import org.kantega.reststop.api.ReststopPlugin;

import java.util.Collection;

/**
 *
 */
public interface CustomAppPlugin extends ReststopPlugin {

    Collection<String> getGreetings();
}
