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

package org.kantega.reststop.webjars;

import org.kantega.reststop.api.ReststopPluginManager;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;

/**
 * Scans META-INF/resource of all plugins for webjars related resources, and then adds them to response.
 *
 * TODO: Add caching of resources
 */
public class WebJarsFilter implements Filter {


    private final ReststopPluginManager reststopPluginManager;

    public WebJarsFilter(ReststopPluginManager reststopPluginManager) {
        this.reststopPluginManager = reststopPluginManager;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) servletRequest;
        HttpServletResponse res = (HttpServletResponse) servletResponse;

        String contextRelative = req.getRequestURI().substring(req.getContextPath().length());

        String path = "META-INF/resources" + contextRelative;
        for(ClassLoader loader : reststopPluginManager.getPluginClassLoaders()){

            URL resource = loader.getResource(path);

            if (resource != null && resource.getPath().endsWith("/") && !path.endsWith("/")) {
                filterChain.doFilter(req, res);
                return;
            }

            if(resource != null) {
                String mimeType = req.getServletContext().getMimeType(path.substring(path.lastIndexOf("/") + 1));
                if(mimeType != null) {
                    res.setContentType(mimeType);
                }

                try(InputStream in=resource.openStream()){
                    copy(in, res.getOutputStream());
                };

                return;
            }
        }
        filterChain.doFilter(req, res);
    }

    private void copy(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[1024 * 4];
        int n;
        while (-1 != (n = input.read(buffer))) {
            output.write(buffer, 0, n);
        }
    }

    @Override
    public void destroy() {

    }
}
