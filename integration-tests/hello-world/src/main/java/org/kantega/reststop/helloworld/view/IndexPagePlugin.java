/*
 * Copyright 2015 Kantega AS
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

package org.kantega.reststop.helloworld.view;

import org.kantega.reststop.api.Export;
import org.kantega.reststop.api.Plugin;
import org.kantega.reststop.api.ServletBuilder;

import javax.servlet.Filter;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 *
 */
@Plugin
public class IndexPagePlugin  {

    @Export
    private final Filter indexServlet;

    @Export
    private final Filter cssServlet;
    @Export
    private final Filter helloWorldServlet;

    public IndexPagePlugin(ServletBuilder servletBuilder) {
        indexServlet = servletBuilder.resourceServlet("/", getClass().getResource("index.html"));
        cssServlet = servletBuilder.resourceServlet("/ws.css", getClass().getResource("ws.css"));

        helloWorldServlet = servletBuilder.servlet(new HttpServlet() {
            @Override
            protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                resp.getWriter().print("halloOo");
            }
        }, "/heiverden");
    }
}

