/*
 * Copyright 2018 Kantega AS
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

package org.kantega.reststop.assets;

import org.kantega.reststop.classloaderutils.PluginClassLoader;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collection;

/**
 *
 */
public class AssetFilter implements Filter {

    private final Collection<PluginClassLoader> pluginClassLoaders;
    private final String classpathPrefix;
    private final String filterMapping;

    public AssetFilter(Collection<PluginClassLoader> pluginClassLoaders, String classpathPrefix, String filterMapping) {
        this.pluginClassLoaders = pluginClassLoaders;
        this.classpathPrefix = classpathPrefix;
        this.filterMapping = filterMapping;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) servletRequest;
        HttpServletResponse resp = (HttpServletResponse) servletResponse;

        String contextRelative = req.getRequestURI().substring(req.getContextPath().length());

        final String path = classpathPrefix +contextRelative.substring(filterMapping.length());

        for(PluginClassLoader loader : pluginClassLoaders) {



            URL resource = loader.getResource(path);


            if(resource != null && !path.endsWith("/") && isDirectoryResource(resource, loader, path)) {
                resp.sendRedirect(req.getRequestURI() +"/");
                return;

            }
            if(path.endsWith("/")) {
                resource = loader.getResource(path +"index.html");
            }

            if(resource != null) {

                String mimeType = req.getServletContext().getMimeType(path.substring(path.lastIndexOf("/") + 1));
                if(mimeType != null) {
                    resp.setContentType(mimeType);
                }

                URLConnection urlConnection = resource.openConnection();

                if(isUnmodified(urlConnection, req, resp)) {
                    return;
                } else {

                    long contentLength = urlConnection.getContentLengthLong();

                    if (contentLength != -1) {
                        resp.setContentLengthLong(contentLength);
                    }

                    try (InputStream in = urlConnection.getInputStream()) {
                        copy(in, servletResponse.getOutputStream());
                    }
                    return;
                }
            }
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    private boolean isUnmodified(URLConnection urlConnection, HttpServletRequest req, HttpServletResponse resp) throws IOException {
        long lastModified = urlConnection.getLastModified();
        if(lastModified != 0) {
            resp.addDateHeader("Last-Modified", lastModified);
            long ifModifiedSince = req.getDateHeader("If-Modified-Since");
            if(ifModifiedSince != -1 && lastModified <= ifModifiedSince) {
                resp.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                return true;
            }

        }
        return false;
    }


    private boolean isDirectoryResource(URL resource, ClassLoader loader, String path) {

        try {
            if("file".equals(resource.getProtocol()) && new File(resource.toURI().getPath()).isDirectory()) {
                return true;

            } else if("jar".equals(resource.getProtocol()) && loader.getResource(path +"/") != null) {
                return true;
            }
            return false;
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }

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

