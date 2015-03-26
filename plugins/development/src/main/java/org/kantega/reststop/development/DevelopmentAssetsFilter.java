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

package org.kantega.reststop.development;

import org.apache.commons.io.IOUtils;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URL;


/**
 *
 */
public class DevelopmentAssetsFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse resp = (HttpServletResponse) response;

        String contextRelative = req.getRequestURI().substring(req.getContextPath().length());

        String path = "development/" +contextRelative.substring("/dev/assets/".length());


        ClassLoader loader = getClass().getClassLoader();
        URL resource = loader.getResource(path);


        if(resource != null
                && resource.getPath().endsWith("/")
                && !path.endsWith("/")) {
            resp.sendRedirect(req.getRequestURI() +"/");
            return;

        }
        if(path.endsWith("/")) {
            path +="index.html";
            resource = loader.getResource(path);
        }

        if(resource != null) {
            String mimeType = req.getServletContext().getMimeType(path.substring(path.lastIndexOf("/") + 1));
            if(mimeType != null) {
                resp.setContentType(mimeType);
            }

            IOUtils.copy(resource.openStream(), resp.getOutputStream());

            return;
        }


        chain.doFilter(request, response);
    }

    @Override
    public void destroy() {

    }
}
