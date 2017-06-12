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

import org.kantega.reststop.api.PluginExport;
import org.kantega.reststop.classloaderutils.Artifact;
import org.kantega.reststop.classloaderutils.PluginClassLoader;
import org.kantega.reststop.classloaderutils.PluginInfo;
import org.kantega.reststop.core.ClassLoaderFactory;
import org.kantega.reststop.core.DefaultReststopPluginManager;
import org.kantega.reststop.servlet.api.FilterPhase;
import org.kantega.reststop.servlet.api.ServletBuilder;
import org.kantega.reststop.servlet.api.ServletDeployer;
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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

/**
 *
 */
public class ReststopInitializer implements ServletContainerInitializer{


    @Override
    public void onStartup(Set<Class<?>> classes, ServletContext servletContext) throws ServletException {


        PluginDelegatingFilter pluginDelegatingFilter = new PluginDelegatingFilter();

        DefaultServletBuilder servletBuilder = new DefaultServletBuilder(servletContext, pluginDelegatingFilter);

        Map<Class, Object> staticServices = new HashMap<>();
        staticServices.put(ServletContext.class, servletContext);
        staticServices.put(ServletBuilder.class, servletBuilder);
        staticServices.put(ServletDeployer.class, pluginDelegatingFilter);

        DefaultReststopPluginManager manager = new DefaultReststopPluginManager(getClass().getClassLoader(), findGlobalConfigFile(servletContext), staticServices);
        servletContext.setAttribute("reststopPluginManager", manager);


        FilterRegistration.Dynamic registration = servletContext.addFilter(PluginDelegatingFilter.class.getName(), pluginDelegatingFilter);
        registration.setAsyncSupported(true);
        registration.addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");

        servletContext.addListener(new ShutdownListener(manager));


        deployPlugins(manager, servletContext);

    }

    private void deployPlugins(DefaultReststopPluginManager manager, ServletContext servletContext) throws ServletException {
        List<PluginInfo> plugins = new ArrayList<>();

        plugins.addAll(getExternalPlugins(servletContext));
        plugins.addAll(getWarBundledPlugins(servletContext));

        manager.deploy(plugins, new DefaultClassLoaderFactory());
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
        private final DefaultReststopPluginManager manager;

        public ShutdownListener(DefaultReststopPluginManager manager) {
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


    private List<PluginInfo> getWarBundledPlugins(ServletContext servletContext) {
        String pluginsPath = servletContext.getRealPath("/WEB-INF/reststop/plugins.xml");
        String repositoryPath = servletContext.getRealPath("/WEB-INF/reststop/repository/");
        if(pluginsPath != null && repositoryPath != null) {
            File pluginsFile = new File(pluginsPath);
            File repoDir = new File(repositoryPath);
            if(pluginsFile.exists() && repoDir.exists()) {
                try {
                    Document pluginsXml = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pluginsFile);

                    if(pluginsXml != null) {
                        return getPluginInfos(repoDir, pluginsXml);
                    }
                }  catch (SAXException | IOException | ParserConfigurationException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return Collections.emptyList();
    }

    private List<PluginInfo> getPluginInfos(File repoDir, Document pluginsXml) {
        List<PluginInfo> infos = PluginInfo.parse(pluginsXml);
        resolve(infos, repoDir);
        return infos;
    }

    private List<PluginInfo> getExternalPlugins(ServletContext servletContext) throws ServletException {
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
            return getPluginInfos(repoDir, pluginsXml);
        }
        return Collections.emptyList();
    }

    private void resolve(List<PluginInfo> infos, File repoDir) {
        for (PluginInfo info: infos) {
            if(info.getFile() == null) {
                File pluginJar = getPluginFile(repoDir, info);
                info.setFile(pluginJar);
            }

            for (Artifact artifact : info.getClassPath("runtime")) {
                if(artifact.getFile() == null) {
                    artifact.setFile(getPluginFile(repoDir, artifact));
                }
            }
        }
    }

    private File getPluginFile(File repoDir, Artifact artifact) {
        if (repoDir != null) {
            return new File(repoDir,
                    artifact.getGroupId().replace('.', '/') + "/"
                            + artifact.getArtifactId() + "/"
                            + artifact.getVersion() + "/"
                            + artifact.getArtifactId() + "-" + artifact.getVersion() + ".jar");

        } else {
            return artifact.getFile();
        }
    }


    public static class DefaultServletBuilder implements ServletBuilder {
        private final ServletContext servletContext;
        private PluginDelegatingFilter pluginDelegatingFilter;

        public DefaultServletBuilder(ServletContext servletContext, PluginDelegatingFilter pluginDelegatingFilter) {
            this.servletContext = servletContext;
            this.pluginDelegatingFilter = pluginDelegatingFilter;
        }

        @Override
        public Filter filter(Filter filter, FilterPhase phase, String path, String... additionalPaths) {
            if(filter == null ) {
                throw new IllegalArgumentException("Filter cannot be null");
            }
            if(path == null) {
                throw new IllegalArgumentException("Paths for filter " + filter + " cannot be null");
            }
            if(additionalPaths == null) {
                throw new IllegalArgumentException("Additional paths for filter " + filter + " cannot be null");
            }
            List<String> mappings = new ArrayList<>(Collections.singletonList(path));
            mappings.addAll(asList(additionalPaths));
            return new MappingWrappedFilter(filter, mappings.toArray(new String[mappings.size()]) , phase);
        }

        @Override
        public Filter resourceServlet(URL url, String path, String... additionalPaths) {
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
            }, path, additionalPaths);
        }

        @Override
        public Filter servlet(HttpServlet servlet, String path, String... additionalPaths) {
            if(servlet == null ) {
                throw new IllegalArgumentException("Servlet parameter cannot be null");
            }
            if(path == null) {
                throw new IllegalArgumentException("Path for servlet " +servlet + " cannot be null");
            }
            if(additionalPaths == null) {
                throw new IllegalArgumentException("Additional paths for servlet " +servlet + " cannot be null");
            }
            return filter(new ServletWrapperFilter(servlet), FilterPhase.USER, path, additionalPaths);
        }


        @Override
        public RedirectBuilder redirectFrom(String fromPath, String... additionalFromPaths) {
            return location -> servlet(new HttpServlet() {
                @Override
                protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
                    resp.sendRedirect(location);
                }
            }, fromPath, additionalFromPaths);
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
            return pluginDelegatingFilter.buildFilterChain(orig.getRequest(), orig.getFilterChain());
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

            public ServletWrapperFilter(final HttpServlet servlet) {
                this.servlet = servlet;
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
                        return getMappedServletPath();
                    }

                    @Override
                    public String getPathInfo() {
                        String requestURI = getRequestURI();
                        return requestURI.substring(super.getContextPath().length() + getMappedServletPath().length());
                    }

                    String getMappedServletPath(){
                        String servletPath = (String) req.getAttribute(MappingWrappedFilter.MATCHED_MAPPING);
                        while(servletPath.endsWith("*") || servletPath.endsWith("/")) {
                            servletPath = servletPath.substring(0, servletPath.length()-1);
                        }
                        return servletPath;
                    }
                }, resp);

            }

            @Override
            public void destroy() {

            }
        }
    }



    static class MappingWrappedFilter implements Filter {
        static final String MATCHED_MAPPING = "MATCHED_MAPPING";
        private final Filter filter;
        private final String[] mappings;
        private final FilterPhase phase;

        public MappingWrappedFilter(Filter filter, String[] mappings, FilterPhase phase) {
            this.filter = filter;
            this.mappings = mappings;
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
            for (String mapping : mappings) {
                if(mapping.equals(contextRelative) || mapping.endsWith("*") && contextRelative.regionMatches(0, mapping, 0, mapping.length()-1)){
                    req.setAttribute(MATCHED_MAPPING, mapping);
                    return true;
                }
            }
            return false;
        }



        @Override
        public void destroy() {

        }
    }

    public static class PluginDelegatingFilter implements Filter, ServletDeployer {

        private volatile Collection<PluginExport<Filter>> filters = Collections.emptyList();

        private final Comparator<PluginExport<Filter>> comparator =
                Comparator.comparing(e -> (e.getExport() instanceof MappingWrappedFilter) ? ((MappingWrappedFilter)e.getExport()).phase.ordinal() : FilterPhase.USER.ordinal());
        @Override
        public void init(FilterConfig filterConfig) throws ServletException {

        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

            servletResponse.setCharacterEncoding("utf-8");
            buildFilterChain((HttpServletRequest) servletRequest, filterChain).doFilter(servletRequest, servletResponse);
        }


        protected FilterChain buildFilterChain(HttpServletRequest request, FilterChain filterChain) {
            Iterator<PluginExport<Filter>> matchingFilters = this.filters.stream()
                    .filter(e -> isMatching(request, e))
                    .iterator();
            return new PluginFilterChain(request, matchingFilters, filterChain);
        }

        private boolean isMatching(HttpServletRequest request, PluginExport<Filter> filterExport) {
            if(filterExport.getExport() instanceof MappingWrappedFilter) {
                return ((MappingWrappedFilter)filterExport.getExport()).mappingMatchesRequest(request);
            } else {
                return true;
            }
        }
        @Override
        public void destroy() {

        }

        @Override
        public void deploy(Collection<PluginExport<Filter>> filters) {
            this.filters = filters.stream()
                    .sorted(comparator)
                    .collect(Collectors.toList());
        }
    }

    private static class PluginFilterChain implements FilterChain {
        private final FilterChain filterChain;
        private final HttpServletRequest request;
        private final Iterator<PluginExport<Filter>> filters;

        public PluginFilterChain(HttpServletRequest request, Iterator<PluginExport<Filter>> filters, FilterChain filterChain) {
            this.request = request;
            this.filters = filters;
            this.filterChain = filterChain;
        }
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            if(filters.hasNext()) {
                PluginExport<Filter> filterExport = filters.next();

                Filter filter = filterExport.getExport() instanceof MappingWrappedFilter ? ((MappingWrappedFilter)filterExport.getExport()).filter : filterExport.getExport();

                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                try {
                    Thread.currentThread().setContextClassLoader(filterExport.getClassLoader());
                    filter.doFilter(request, response, this);
                } finally {
                    Thread.currentThread().setContextClassLoader(loader);
                }
            } else {
                filterChain.doFilter(request, response);
            }
        }

        private FilterChain getFilterChain() {
            return filterChain;
        }

        public HttpServletRequest getRequest() {
            return request;
        }
    }





    private class DefaultClassLoaderFactory implements ClassLoaderFactory {
        @Override
        public PluginClassLoader createPluginClassLoader(PluginInfo pluginInfo, ClassLoader parentClassLoader, List<PluginInfo> allPlugins) {
            try {
                PluginClassLoader loader = new PluginClassLoader(pluginInfo, parentClassLoader);

                loader.addURL(pluginInfo.getFile().toURI().toURL());
                for (Artifact artifact : pluginInfo.getClassPath("runtime")) {
                    if(allPlugins.stream().noneMatch(p -> p.getPluginId().equals(artifact.getPluginId()))) {
                        loader.addURL(artifact.getFile().toURI().toURL());
                    }
                }
                return loader;
            } catch (MalformedURLException e) {
                throw new RuntimeException(e);
            }
        }
    }
}
