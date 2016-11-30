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

                try (InputStream in = resource.openStream()) {
                    copy(in, servletResponse.getOutputStream());
                }
                return;
            }
        }

        filterChain.doFilter(servletRequest, servletResponse);
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

