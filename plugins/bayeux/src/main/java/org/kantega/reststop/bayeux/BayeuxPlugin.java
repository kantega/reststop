/*
 * Copyright 2016 Kantega AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.kantega.reststop.bayeux;

import org.cometd.server.CometDServlet;
import org.kantega.reststop.api.Config;
import org.kantega.reststop.api.Export;
import org.kantega.reststop.api.Plugin;
import org.kantega.reststop.api.ServletBuilder;

import javax.annotation.PreDestroy;
import javax.servlet.Filter;
import javax.servlet.ServletException;
import java.util.Objects;
import java.util.Properties;

@Plugin
public class BayeuxPlugin {

    private final CometDServlet cometDServlet;

    private final boolean servletFilterAsyncSupported;

    private final Properties initParams;

    @Export
    private final BayeuxServiceManager bayeuxServiceManager;

    @Export
    private final BayeuxBroadcastManager bayeuxBroadcastManager;

    @Export
    private final Filter bayeuxFilter;

    public BayeuxPlugin(
            @Config(required = true) final Boolean servletFilterAsyncSupported,
            @Config(defaultValue = "/bayeux/*") final String bayeuxFilterMapping,
            final ServletBuilder reststop) throws ServletException {
        this.servletFilterAsyncSupported = requireValue(servletFilterAsyncSupported, true, "Must be true: servletFilterAsyncSupported");
        cometDServlet = new CometDServlet();
        initParams = new Properties();
        initParams.setProperty("cometdURLMapping", bayeuxFilterMapping);
        cometDServlet.init(reststop.servletConfig("bayeux", initParams));
        //TODO configure bayeuxServer
        //TODO: name = clientIdPrefix
        //TODO: maxThreads
        bayeuxFilter = reststop.servlet(cometDServlet, bayeuxFilterMapping);
        bayeuxServiceManager = new ReststopBayeuxServiceManager(cometDServlet.getBayeux(), null, null);
        bayeuxBroadcastManager = new ReststopBayeuxBroadcastManager(cometDServlet.getBayeux(), null);

    }

    @PreDestroy
    public void destroy() {
        cometDServlet.destroy();
    }

    public static <T> T requireValue(T actual, T expected, String message) {
        if (Objects.equals(actual, expected)) {
            return actual;
        }
        throw new IllegalArgumentException(message);
    }




}
