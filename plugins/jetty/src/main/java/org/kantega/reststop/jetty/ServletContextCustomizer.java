package org.kantega.reststop.jetty;

import org.eclipse.jetty.servlet.ServletContextHandler;

import javax.servlet.ServletException;

/**
 *
 */
public interface ServletContextCustomizer {
    void customize(ServletContextHandler contextHandler) throws ServletException;
}
