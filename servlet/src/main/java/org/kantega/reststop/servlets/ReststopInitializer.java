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

package org.kantega.reststop.servlets;

import org.kantega.jexmec.ClassLoaderProvider;
import org.kantega.jexmec.ServiceKey;
import org.kantega.reststop.api.FilterPhase;
import org.kantega.reststop.api.PluginExport;
import org.kantega.reststop.api.ReststopPluginManager;
import org.kantega.reststop.api.ServletBuilder;
import org.kantega.reststop.classloaderutils.PluginInfo;
import org.kantega.reststop.core.PluginManagerBuilder;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.*;

import static org.kantega.reststop.classloaderutils.PluginInfo.configure;

/**
 *
 */
public class ReststopInitializer implements ServletContainerInitializer{


    @Override
    public void onStartup(Set<Class<?>> classes, ServletContext servletContext) throws ServletException {

        File globalConfigFile = findGlobalConfigFile(servletContext);

        ClassLoaderProvider[] classLoaderProviders = findClassLoaderProviders(servletContext, globalConfigFile);
        DefaultServletBuilder servletBuilder = new DefaultServletBuilder(servletContext);
        PluginManagerBuilder.DefaultReststopPluginManager manager = PluginManagerBuilder.builder()
                .withService(ServiceKey.by(ServletContext.class), servletContext)
                .withService(ServiceKey.by(ServletBuilder.class), servletBuilder)
                .withClassLoaderProviders(classLoaderProviders)
                .beforeStart(servletBuilder::setManager)
                .build();



        servletContext.setAttribute("reststopPluginManager", manager);


        servletContext.addFilter(PluginDelegatingFilter.class.getName(), new PluginDelegatingFilter(manager))
                .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");

        servletContext.addListener(new ShutdownListener(manager));

    }

    private File findGlobalConfigFile(ServletContext servletContext) throws ServletException {
        String configDirectory = requiredInitParam(servletContext, "pluginConfigurationDirectory");
        String applicationName = requiredInitParam(servletContext, "applicationName");

        File globalConfigurationFile = new File(configDirectory, applicationName +".conf");
        if(!globalConfigurationFile.exists()) {
            throw new ServletException("Configuration file does not exist: " + globalConfigurationFile.getAbsolutePath());
        }

        return globalConfigurationFile;

    }

    private String requiredInitParam(ServletContext servletContext, String paramName) throws ServletException {
        String value = initParam(servletContext, paramName);
        if(value == null) {
            throw new ServletException("You web application is missing a required servlet context-param '" + paramName + "'");
        }
        return value;
    }

    private String initParam(ServletContext servletContext, String paramName) throws ServletException {
        String value = servletContext.getInitParameter(paramName);
        if (value == null) value = System.getProperty(paramName);
        return value;
    }

    private static class ShutdownListener implements ServletContextListener {
        private final PluginManagerBuilder.DefaultReststopPluginManager manager;

        public ShutdownListener(PluginManagerBuilder.DefaultReststopPluginManager manager) {
            this.manager = manager;
        }

        @Override
        public void contextInitialized(ServletContextEvent sce) {

        }

        @Override
        public void contextDestroyed(ServletContextEvent sce) {
            manager.stop();
        }
    }





    private ClassLoaderProvider[] findClassLoaderProviders(ServletContext servletContext, File globalConfigFile) throws ServletException {
        List<ClassLoaderProvider> providers = new ArrayList<>();

        addExternalProvider(servletContext, providers, globalConfigFile);
        addWarBundledProvider(servletContext, providers, globalConfigFile);
        return providers.toArray(new ClassLoaderProvider[providers.size()]);

    }

    private void addWarBundledProvider(ServletContext servletContext, List<ClassLoaderProvider> providers, File globalConfigFile) {
        String pluginsPath = servletContext.getRealPath("/WEB-INF/reststop/plugins.xml");
        String repositoryPath = servletContext.getRealPath("/WEB-INF/reststop/repository/");
        if(pluginsPath != null && repositoryPath != null) {
            File pluginsFile = new File(pluginsPath);
            File repoDir = new File(repositoryPath);
            if(pluginsFile.exists() && repoDir.exists()) {
                try {
                    Document pluginsXml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pluginsFile);

                    if(pluginsXml != null) {
                        addPluginClassLoaderProvider(pluginsXml, repoDir, providers, globalConfigFile);
                    }
                }  catch (SAXException | IOException | ParserConfigurationException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }

    private void addExternalProvider(ServletContext servletContext, List<ClassLoaderProvider> providers, File globalConfigFile) throws ServletException {
        Document pluginsXml = (Document) servletContext.getAttribute("pluginsXml");
        String repoPath = initParam(servletContext, "repositoryPath");
        File repoDir = null;

        if(repoPath != null) {
            repoDir = new File(repoPath);
            if(!repoDir.exists()) {
                throw new ServletException("repositoryPath does not exist: " + repoDir);
            }
            if(!repoDir.isDirectory()) {
                throw new ServletException("repositoryPath is not a directory: " + repoDir);
            }
        }
        if(pluginsXml == null) {

            String path = initParam(servletContext, "plugins.xml");
            if(path != null) {
                try {
                    pluginsXml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File(path));
                    servletContext.setAttribute("pluginsXml", pluginsXml);
                } catch (SAXException | IOException | ParserConfigurationException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        if(pluginsXml != null) {
            addPluginClassLoaderProvider(pluginsXml, repoDir, providers, globalConfigFile);
        }
    }

    private void addPluginClassLoaderProvider(Document pluginsXml, File repoDir, List<ClassLoaderProvider> providers, File globalConfigurationFile) {
        List<PluginInfo> parsed = PluginInfo.parse(pluginsXml);
        configure(parsed, globalConfigurationFile);
        providers.add(new PluginManagerBuilder.PluginInfosClassLoaderProvider(parsed, repoDir));
    }




    private static class DefaultServletBuilder implements ServletBuilder {
        private final ServletContext servletContext;
        private ReststopPluginManager manager;

        private DefaultServletBuilder(ServletContext servletContext) {
            this.servletContext = servletContext;
        }


        public void setManager(ReststopPluginManager manager) {
            this.manager = manager;
        }

        @Override
        public Filter filter(Filter filter, String mapping, FilterPhase phase) {
            if(filter == null ) {
                throw new IllegalArgumentException("Filter cannot be null");
            }
            if(mapping == null) {
                throw new IllegalArgumentException("Mapping for filter " + filter + " cannot be null");
            }
            return new MappingWrappedFilter(filter, mapping, phase);
        }

        @Override
        public Filter resourceServlet(String path, URL url) {
            return servlet(new HttpServlet() {
                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                    String mediaType = servletContext.getMimeType(path);
                    if(mediaType == null) {
                        mediaType = "text/html";
                    }
                    if(mediaType.equals("text/html")) {
                        resp.setCharacterEncoding("utf-8");
                    }
                    resp.setContentType(mediaType);

                    OutputStream output = resp.getOutputStream();

                    try (InputStream input = url.openStream()){
                        byte[] buffer = new byte[1024];
                        int n;
                        while (-1 != (n = input.read(buffer))) {
                            output.write(buffer, 0, n);
                        }
                    }
                }
            }, path);
        }

        @Override
        public Filter servlet(HttpServlet servlet, String path) {
            if(servlet == null ) {
                throw new IllegalArgumentException("Servlet parameter cannot be null");
            }
            if(path == null) {
                throw new IllegalArgumentException("Path for servlet " +servlet + " cannot be null");
            }
            return filter(new ServletWrapperFilter(servlet, path), path, FilterPhase.USER);
        }


        @Override
        public Filter redirectServlet(String path, String location) {
            return servlet(new HttpServlet() {
                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                    resp.sendRedirect(location);
                }
            }, path);
        }

        @Override
        public ServletConfig servletConfig(String name, Properties properties) {
            return new PropertiesWebConfig(name, properties, servletContext);
        }

        @Override
        public FilterConfig filterConfig(String name, Properties properties) {
            return new PropertiesWebConfig(name, properties, servletContext);
        }
        @Override
        public FilterChain newFilterChain(FilterChain filterChain) {

            PluginFilterChain orig = (PluginFilterChain) filterChain;
            return buildFilterChain(orig.getRequest(), orig.getFilterChain(), manager);
        }

        private static class PropertiesWebConfig implements ServletConfig, FilterConfig  {
            private final String name;
            private final Properties properties;
            private final ServletContext servletContext;

            public PropertiesWebConfig(String name, Properties properties, ServletContext servletContext) {
                this.name = name;
                this.properties = properties;
                this.servletContext = servletContext;
            }

            @Override
            public String getFilterName() {
                return name;
            }

            @Override
            public String getServletName() {
                return name;
            }

            @Override
            public ServletContext getServletContext() {
                return servletContext;
            }

            @Override
            public String getInitParameter(String name) {
                return properties.getProperty(name);
            }

            @Override
            public Enumeration<String> getInitParameterNames() {
                return Collections.enumeration(properties.stringPropertyNames());
            }
        }

        private static class ServletWrapperFilter implements Filter {
            private final HttpServlet servlet;
            private final String servletPath;

            public ServletWrapperFilter(final HttpServlet servlet, final String mapping) {
                this.servlet = servlet;
                String servletPath = mapping;
                while(servletPath.endsWith("*") || servletPath.endsWith("/")) {
                    servletPath = servletPath.substring(0, servletPath.length()-1);
                }
                this.servletPath = servletPath;

            }

            @Override
            public void init(FilterConfig filterConfig) throws ServletException {

            }

            @Override
            public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
                HttpServletRequest req = (HttpServletRequest) servletRequest;
                HttpServletResponse resp = (HttpServletResponse) servletResponse;



                servlet.service(new HttpServletRequestWrapper(req) {
                    @Override
                    public String getServletPath() {
                        return servletPath;
                    }

                    @Override
                    public String getPathInfo() {
                        String requestURI = getRequestURI();
                        return requestURI.substring(super.getContextPath().length() + servletPath.length());
                    }
                }, resp);

            }

            @Override
            public void destroy() {

            }
        }
    }



    private static class MappingWrappedFilter implements Filter {
        private final Filter filter;
        private final String mapping;
        private final FilterPhase phase;

        public MappingWrappedFilter(Filter filter, String mapping, FilterPhase phase) {
            this.filter = filter;
            this.mapping = mapping;
            this.phase = phase;
        }

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {

        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
            HttpServletRequest req = (HttpServletRequest) servletRequest;

            if(mappingMatchesRequest(req)) {
                filter.doFilter(servletRequest, servletResponse, filterChain);
            } else {
                filterChain.doFilter(servletRequest, servletResponse);
            }
        }

        private boolean mappingMatchesRequest(HttpServletRequest req) {
            String contextRelative = req.getRequestURI().substring(req.getContextPath().length());
            return mapping.equals(contextRelative) || mapping.endsWith("*") && contextRelative.regionMatches(0, mapping, 0, mapping.length()-1);
        }



        @Override
        public void destroy() {

        }
    }

    private static class PluginDelegatingFilter implements Filter {
        private final ReststopPluginManager manager;

        public PluginDelegatingFilter(ReststopPluginManager manager) {
            this.manager = manager;
        }

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {

        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

            servletResponse.setCharacterEncoding("utf-8");
            buildFilterChain((HttpServletRequest) servletRequest, filterChain, manager).doFilter(servletRequest, servletResponse);
        }



        @Override
        public void destroy() {

        }
    }

    private static class ClassLoaderFilter {
        final ClassLoader classLoader;
        final Filter filter;

        private ClassLoaderFilter(ClassLoader classLoader, Filter filter) {
            this.classLoader = classLoader;
            this.filter = filter;
        }
    }
    private static FilterChain buildFilterChain(HttpServletRequest request, FilterChain filterChain, ReststopPluginManager pluginManager) {
        List<ClassLoaderFilter> filters = new ArrayList<>();

        for (PluginExport<Filter> pluginExport : pluginManager.findPluginExports(Filter.class)) {
            Filter filter = pluginExport.getExport();
            if(filter instanceof MappingWrappedFilter) {
                MappingWrappedFilter mwf = (MappingWrappedFilter) filter;
                if(! mwf.mappingMatchesRequest(request)) {
                    continue;
                }
            }
            filters.add(new ClassLoaderFilter(pluginExport.getClassLoader(), filter));
        }

        filters.add(new ClassLoaderFilter(AssetFilter.class.getClassLoader(), new MappingWrappedFilter(new AssetFilter(pluginManager), "/assets/*", FilterPhase.USER)));

        Collections.sort(filters, new Comparator<ClassLoaderFilter>() {
            @Override
            public int compare(ClassLoaderFilter o1, ClassLoaderFilter o2) {
                FilterPhase phase1 = o1.filter instanceof MappingWrappedFilter ? ((MappingWrappedFilter)o1.filter).phase : FilterPhase.USER;
                FilterPhase phase2 = o2.filter instanceof MappingWrappedFilter ? ((MappingWrappedFilter)o2.filter).phase : FilterPhase.USER;
                return phase1.ordinal() - phase2.ordinal();
            }
        });
        return new PluginFilterChain(request, filters, filterChain);
    }
    private static class PluginFilterChain implements FilterChain {
        private final List<ClassLoaderFilter> filters;
        private final FilterChain filterChain;
        private int filterIndex;
        private final HttpServletRequest request;

        public PluginFilterChain(HttpServletRequest request, List<ClassLoaderFilter> filters, FilterChain filterChain) {
            this.request = request;
            this.filters = filters;
            this.filterChain = filterChain;
        }
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            if(filterIndex == filters.size()) {
                filterChain.doFilter(request, response);
            } else {
                ClassLoader loader = Thread.currentThread().getContextClassLoader();

                try {
                    ClassLoaderFilter classLoaderFilter = filters.get(filterIndex++);
                    Thread.currentThread().setContextClassLoader(classLoaderFilter.classLoader);
                    classLoaderFilter.filter.doFilter(request, response, this);
                } finally {
                    Thread.currentThread().setContextClassLoader(loader);
                }
            }
        }

        private FilterChain getFilterChain() {
            return filterChain;
        }

        public HttpServletRequest getRequest() {
            return request;
        }
    }



    private static class AssetFilter implements Filter {
        private final ReststopPluginManager manager;

        public AssetFilter(ReststopPluginManager manager) {
            this.manager = manager;
        }

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {

        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

            HttpServletRequest req = (HttpServletRequest) servletRequest;
            HttpServletResponse resp = (HttpServletResponse) servletResponse;

            String contextRelative = req.getRequestURI().substring(req.getContextPath().length());

            final String path = "assets/" +contextRelative.substring("/assets/".length());

            for(ClassLoader loader : manager.getPluginClassLoaders()) {



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
}
