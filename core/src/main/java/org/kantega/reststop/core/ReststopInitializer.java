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

package org.kantega.reststop.core;

import org.kantega.jexmec.*;
import org.kantega.jexmec.ctor.ConstructorInjectionPluginLoader;
import org.kantega.jexmec.manager.DefaultPluginManager;
import org.kantega.reststop.api.*;
import org.kantega.reststop.classloaderutils.*;
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
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.logging.Logger;

import static org.kantega.jexmec.manager.DefaultPluginManager.buildFor;
import static org.kantega.reststop.classloaderutils.PluginInfo.configure;

/**
 *
 */
public class ReststopInitializer implements ServletContainerInitializer{


    private static Logger log = Logger.getLogger(ReststopInitializer.class.getName());

    @Override
    public void onStartup(Set<Class<?>> classes, ServletContext servletContext) throws ServletException {

        File globalConfigFile = findGlobalConfigFile(servletContext);

        DefaultReststopPluginManager reststopPluginManager = new DefaultReststopPluginManager();

        final DefaultPluginManager<ReststopPlugin> manager = buildPluginManager(servletContext, reststopPluginManager, globalConfigFile);

        reststopPluginManager.setManager(manager);

        servletContext.setAttribute("reststopPluginManager", manager);

        manager.addPluginManagerListener(new PluginLifecyleListener(manager));

        manager.start();

        servletContext.addFilter(PluginDelegatingFilter.class.getName(), new PluginDelegatingFilter(reststopPluginManager))
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
        String value = servletContext.getInitParameter(paramName);
        if(value == null) {
            throw new ServletException("You web application is missing a required servlet context-param '" + paramName + "'");
        }
        return value;
    }

    private static class ShutdownListener implements ServletContextListener {
        private final DefaultPluginManager<ReststopPlugin> manager;

        public ShutdownListener(DefaultPluginManager<ReststopPlugin> manager) {
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

    private DefaultPluginManager<ReststopPlugin> buildPluginManager(ServletContext servletContext, DefaultReststopPluginManager reststopPluginManager, File globalConfigFile) throws ServletException {

        DefaultReststop reststop = new DefaultReststop(servletContext);

        final PluginExportsServiceLocator exportsServiceLocator = new PluginExportsServiceLocator();
        DefaultPluginManager<ReststopPlugin> manager = buildFor(ReststopPlugin.class)
                .withClassLoaderProvider(reststop)
                .withClassLoaderProviders(findClassLoaderProviders(servletContext, globalConfigFile))
                .withClassLoader(getClass().getClassLoader())
                .withPluginLoader(new ConstructorInjectionPluginLoader())
                .withService(ServiceKey.by(Reststop.class), reststop)
                .withService(ServiceKey.by(ServletContext.class), servletContext)
                .withService(ServiceKey.by(ReststopPluginManager.class), reststopPluginManager)
                .withServiceLocator(exportsServiceLocator)
                .build();

        exportsServiceLocator.setPluginManager(manager);

        reststop.setManager(reststopPluginManager);
        return manager;
    }

    private static class PluginExportsServiceLocator implements ServiceLocator {
        private final Map<ClassLoader, Map<ServiceKey, Object>> servicesByClassLoader = new IdentityHashMap<>();


        private void setPluginManager(PluginManager<ReststopPlugin> pluginManager) {
            pluginManager.addPluginManagerListener(new PluginManagerListener<ReststopPlugin>() {
                @Override
                public  void beforeActivation(PluginManager<ReststopPlugin> pluginManager, ClassLoaderProvider classLoaderProvider, ClassLoader classLoader, PluginLoader<ReststopPlugin> pluginLoader, Collection<ReststopPlugin> plugins) {
                    synchronized (servicesByClassLoader) {
                        for (ReststopPlugin plugin : plugins) {

                            for(Field field : plugin.getClass().getDeclaredFields()) {
                                if(field.getAnnotation(Export.class) != null ) {
                                    try {
                                        field.setAccessible(true);
                                        Object service = field.get(plugin);
                                        if(service != null) {
                                            if(!servicesByClassLoader.containsKey(classLoader)) {
                                                servicesByClassLoader.put(classLoader, new HashMap<ServiceKey, Object>());
                                            }
                                            Map<ServiceKey, Object> forClassLoader = servicesByClassLoader.get(classLoader);

                                            forClassLoader.put(ServiceKey.by(field.getType()), service);
                                        }
                                    } catch (IllegalAccessException e) {
                                        throw new RuntimeException(e);
                                    }

                                }
                            }


                        }
                    }
                }

                @Override
                public  void beforeClassLoaderRemoved(PluginManager<ReststopPlugin> pluginManager, ClassLoaderProvider classLoaderProvider, ClassLoader classLoader) {
                    synchronized (servicesByClassLoader) {
                        servicesByClassLoader.remove(classLoader);
                    }
                }
            });

        }

        @Override
        public Set<ServiceKey> keySet() {
            synchronized (servicesByClassLoader) {
                Set<ServiceKey> keys = new HashSet<>();
                for (Map<ServiceKey, Object> forClassloader : servicesByClassLoader.values()) {
                    keys.addAll(forClassloader.keySet());
                }
                return keys;
            }

        }

        @Override
        public <T> T get(ServiceKey<T> serviceKey) {
            synchronized (servicesByClassLoader) {
                for (Map<ServiceKey, Object> forClassLoader : servicesByClassLoader.values()) {
                    Object impl = forClassLoader.get(serviceKey);
                    if(impl != null) {
                        return serviceKey.getType().cast(impl);
                    }
                }
                return null;

            }
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

    private void addPluginClassLoaderProvider(Document pluginsXml, File repoDir, List<ClassLoaderProvider> providers, File globalConfigurationFile) {
        List<PluginInfo> parsed = PluginInfo.parse(pluginsXml);
        configure(parsed, globalConfigurationFile);
        providers.add(new PluginInfosClassLoaderProvider(parsed, repoDir));
    }

    private void addExternalProvider(ServletContext servletContext, List<ClassLoaderProvider> providers, File globalConfigFile) throws ServletException {
        Document pluginsXml = (Document) servletContext.getAttribute("pluginsXml");
        String repoPath = servletContext.getInitParameter("repositoryPath");
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

            String path = servletContext.getInitParameter("plugins.xml");
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






    private static class DefaultReststop implements Reststop, ClassLoaderProvider {
        private final ServletContext servletContext;
        private Registry registry;
        private ClassLoader parentClassLoader;
        private ReststopPluginManager manager;

        public DefaultReststop(ServletContext servletContext) {

            this.servletContext = servletContext;
        }

        @Override
        public void start(Registry registry, ClassLoader parentClassLoader) {

            this.registry = registry;
            this.parentClassLoader = parentClassLoader;
        }

        @Override
        public void stop() {

        }

        @Override
        public ClassLoader getPluginParentClassLoader() {
            return parentClassLoader;
        }

        @Override
        public PluginClassLoaderChange changePluginClassLoaders() {
            return new DefaultClassLoaderChange(registry);
        }

        @Override
        public Filter createFilter(Filter filter, String mapping, FilterPhase phase) {
            if(filter == null ) {
                throw new IllegalArgumentException("Filter cannot be null");
            }
            if(mapping == null) {
                throw new IllegalArgumentException("Mapping for filter " + filter + " cannot be null");
            }
            return new MappingWrappedFilter(filter, mapping, phase);
        }

        @Override
        public Filter createServletFilter(HttpServlet servlet, String path) {
            if(servlet == null ) {
                throw new IllegalArgumentException("Servlet parameter cannot be null");
            }
            if(path == null) {
                throw new IllegalArgumentException("Path for servlet " +servlet + " cannot be null");
            }
            return createFilter(new ServletWrapperFilter(servlet, path), path, FilterPhase.USER);
        }

        public void setManager(ReststopPluginManager manager) {
            this.manager = manager;
        }

        @Override
        public ServletConfig createServletConfig(String name, Properties properties) {
            return new PropertiesWebConfig(name, properties, servletContext);
        }

        @Override
        public FilterConfig createFilterConfig(String name, Properties properties) {
            return new PropertiesWebConfig(name, properties, servletContext);
        }

        @Override
        public FilterChain newFilterChain(FilterChain filterChain) {

            PluginFilterChain orig = (PluginFilterChain) filterChain;
            return buildFilterChain(orig.getRequest(), orig.getFilterChain(), manager);
        }

        private static class DefaultClassLoaderChange implements PluginClassLoaderChange {
            private final Registry registry;
            private final List<ClassLoader> adds = new ArrayList<>();
            private final List<ClassLoader> removes = new ArrayList<>();

            public DefaultClassLoaderChange(Registry registry) {
                this.registry = registry;
            }

            @Override
            public PluginClassLoaderChange add(ClassLoader classLoader) {
                adds.add(classLoader);
                return this;
            }

            @Override
            public PluginClassLoaderChange remove(ClassLoader classLoader) {
                removes.add(classLoader);
                return this;
            }

            @Override
            public void commit() {
                log.info("About to commit class loader change:");
                log.info(" Removing : " + removes);
                for (ClassLoader add : adds) {
                    log.info("Adding " + add);
                    if(add instanceof URLClassLoader) {
                        URLClassLoader ucl = (URLClassLoader) add;
                        for (URL url : ucl.getURLs()) {
                            log.info("\t url: " + url.toString());
                        }
                    }
                }
                registry.replace(removes, adds);
            }
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
        for(ReststopPlugin plugin : pluginManager.getPlugins()) {
            for (Filter filter : plugin.getServletFilters()) {
                if(filter instanceof MappingWrappedFilter) {
                    MappingWrappedFilter mwf = (MappingWrappedFilter) filter;
                    if(! mwf.mappingMatchesRequest(request)) {
                        continue;
                    }
                }
                filters.add(new ClassLoaderFilter(pluginManager.getClassLoader(plugin), filter));
            }
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

    private static class PluginInfosClassLoaderProvider implements ClassLoaderProvider {
        private final List<PluginInfo> pluginInfos;
        private final File repoDir;

        public PluginInfosClassLoaderProvider(List<PluginInfo> pluginInfos, File repoDir) {
            this.pluginInfos = pluginInfos;
            this.repoDir = repoDir;
        }

        @Override
        public void start(Registry registry, ClassLoader parentClassLoader) {
            List<ClassLoader> loaders = new ArrayList<>();
            Map<String, PluginClassLoader> byDep  = new HashMap<>();


            List<PluginInfo> infos = PluginInfo.resolveStartupOrder(pluginInfos);

            for (PluginInfo info : infos) {

                if(info.isDirectDeploy()) {
                    PluginClassLoader pluginClassloader = new PluginClassLoader(info, getParentClassLoader(info, parentClassLoader, byDep));

                    File pluginJar = getPluginFile(info);

                    try {
                        pluginClassloader.addURL(pluginJar.toURI().toURL());

                        for (Artifact artifact : info.getClassPath("runtime")) {
                            pluginClassloader.addURL(getPluginFile(artifact).toURI().toURL());

                        }
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    }

                    loaders.add(pluginClassloader);
                    byDep.put(info.getGroupIdAndArtifactId(), pluginClassloader);

                }
            }

            registry.add(loaders);
        }

        private File getPluginFile(Artifact artifact) {
            if(repoDir != null) {
                return new File(repoDir,
                        artifact.getGroupId().replace('.','/') +"/"
                        + artifact.getArtifactId() +"/"
                        + artifact.getVersion() +"/"
                        + artifact.getArtifactId() + "-" + artifact.getVersion() +".jar");

            } else {
                return artifact.getFile();
            }
        }

        private ClassLoader getParentClassLoader(PluginInfo pluginInfo, ClassLoader parentClassLoader, Map<String, PluginClassLoader> byDep) {
            Set<PluginClassLoader> delegates = new HashSet<>();

            for (Artifact dep : pluginInfo.getDependsOn()) {
                PluginClassLoader dependencyLoader = byDep.get(dep.getGroupIdAndArtifactId());
                if (dependencyLoader != null) {
                    delegates.add(dependencyLoader);
                }
            }
            if (delegates.isEmpty()) {
                return parentClassLoader;
            } else {
                return new ResourceHidingClassLoader(new DelegateClassLoader(parentClassLoader, delegates), ReststopPlugin.class);
            }
        }

        @Override
        public void stop() {

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


                if(resource != null && !path.endsWith("/") && loader.getResource(path +"/") != null ) {
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

    private static class DefaultReststopPluginManager implements ReststopPluginManager{
        private volatile DefaultPluginManager<ReststopPlugin> manager;

        private final IdentityHashMap<ClassLoader, ClassLoader> classLoaders = new IdentityHashMap<>();

        @Override
        public Collection<ReststopPlugin> getPlugins() {
            assertStarted();
            return manager.getPlugins();
        }

        @Override
        public <T extends ReststopPlugin> Collection<T> getPlugins(Class<T> pluginClass) {
            assertStarted();
            return manager.getPlugins(pluginClass);
        }

        @Override
        public ClassLoader getClassLoader(ReststopPlugin plugin) {
            assertStarted();
            return manager.getClassLoader(plugin);
        }

        private void assertStarted() {
            if(manager == null) {
                throw new IllegalStateException("Illegal to call getPlugins before PluginManager is fully started. Please add a listener instead!");
            }
        }

        public void setManager(DefaultPluginManager<ReststopPlugin> manager) {
            this.manager = manager;
            manager.addPluginManagerListener(new PluginManagerListener<ReststopPlugin>() {

                private ThreadLocal<ClassLoader> classLoader = new ThreadLocal<>();

                @Override
                public void afterClassLoaderAdded(PluginManager<ReststopPlugin> pluginManager, ClassLoaderProvider classLoaderProvider, ClassLoader classLoader) {
                    Thread.currentThread().setContextClassLoader(this.classLoader.get());
                    this.classLoader.remove();

                    synchronized (classLoaders) {
                        classLoaders.put(classLoader, classLoader);
                    }

                }

                @Override
                public void beforeClassLoaderRemoved(PluginManager<ReststopPlugin> pluginManager, ClassLoaderProvider classLoaderProvider, ClassLoader classLoader) {
                    synchronized (classLoaders) {
                        classLoaders.remove(classLoader);
                    }
                }

                @Override
                public void beforeClassLoaderAdded(PluginManager<ReststopPlugin> pluginManager, ClassLoaderProvider classLoaderProvider, ClassLoader classLoader) {
                   this.classLoader.set(classLoader);
                    Thread.currentThread().setContextClassLoader(classLoader);
                }




            });
        }

        @Override
        public Collection<ClassLoader> getPluginClassLoaders() {
            ArrayList<ClassLoader> copy;
            synchronized (classLoaders) {
                copy = new ArrayList<>(classLoaders.keySet());
            }
            return copy;
        }
    }

    private static class PluginLifecyleListener extends PluginManagerListener<ReststopPlugin> {

        private boolean pluginManagerStarted;
        private final PluginManager<ReststopPlugin> manager;

        private PluginLifecyleListener(PluginManager<ReststopPlugin> manager) {
            this.manager = manager;
        }

        @Override
        public void afterPluginManagerStarted(PluginManager pluginManager) {
            pluginManagerStarted = true;

            Collection<ReststopPlugin> plugins = manager.getPlugins();
            for(ReststopPlugin plugin : plugins) {
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(manager.getClassLoader(plugin));
                try {

                    for(PluginListener listener : plugin.getPluginListeners()) {
                        listener.pluginManagerStarted();
                    }
                } finally {
                    Thread.currentThread().setContextClassLoader(loader);
                }
            }
        }
        @Override
        public void pluginsUpdated(Collection<ReststopPlugin> plugins) {
            if (pluginManagerStarted) {
                for (ReststopPlugin plugin : manager.getPlugins()) {
                    for (PluginListener listener : plugin.getPluginListeners()) {
                        ClassLoader loader = Thread.currentThread().getContextClassLoader();
                        Thread.currentThread().setContextClassLoader(manager.getClassLoader(plugin));
                        try {
                            listener.pluginsUpdated(plugins);
                        } finally {
                            Thread.currentThread().setContextClassLoader(loader);
                        }
                    }
                }
            }
        }

        @Override
        public void beforePassivation(PluginManager<ReststopPlugin> pluginManager, ClassLoaderProvider classLoaderProvider, ClassLoader classLoader, PluginLoader<ReststopPlugin> pluginLoader, Collection<ReststopPlugin> plugins) {
            for (ReststopPlugin plugin : plugins) {
                plugin.destroy();
            }
        }

        @Override
        public void beforePluginManagerStopped(PluginManager<ReststopPlugin> pluginManager) {
            List<ReststopPlugin> plugins = new ArrayList<>(pluginManager.getPlugins());
            Collections.reverse(plugins);
            for (ReststopPlugin plugin : plugins) {
                plugin.destroy();
            }
        }

        @Override
        public void afterActivation(PluginManager<ReststopPlugin> pluginManager, ClassLoaderProvider classLoaderProvider, ClassLoader classLoader, PluginLoader<ReststopPlugin> pluginLoader, Collection<ReststopPlugin> plugins) {
            for (ReststopPlugin plugin : plugins) {
                plugin.init();
            }
        }
    }
}
