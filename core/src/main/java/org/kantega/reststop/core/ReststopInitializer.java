/*
 * Copyright 2013 Kantega AS
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
import java.net.MalformedURLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.kantega.jexmec.manager.DefaultPluginManager.buildFor;

/**
 *
 */
public class ReststopInitializer implements ServletContainerInitializer{


    private boolean pluginManagerStarted;
    private ServletContext servletContext;

    @Override
    public void onStartup(Set<Class<?>> classes, ServletContext servletContext) throws ServletException {


        this.servletContext = servletContext;

        DefaultReststopPluginManager reststopPluginManager = new DefaultReststopPluginManager();
        final DefaultPluginManager<ReststopPlugin> manager = buildPluginManager(servletContext, reststopPluginManager);
        reststopPluginManager.setManager(manager);

        servletContext.setAttribute("reststopPluginManager", manager);

        manager.addPluginManagerListener(new PluginManagerListener<ReststopPlugin>() {
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
        });
        manager.addPluginManagerListener(new PluginManagerListener<ReststopPlugin>() {
            @Override
            public void afterPluginManagerStarted(PluginManager pluginManager) {
                pluginManagerStarted = true;
                PluginManager<ReststopPlugin> pm = pluginManager;

                Collection<ReststopPlugin> plugins = pm.getPlugins();
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
        });
        manager.start();

        servletContext.addFilter(PluginDelegatingFilter.class.getName(), new PluginDelegatingFilter(manager))
                .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");

        servletContext.addFilter(AssetFilter.class.getName(), new AssetFilter(reststopPluginManager)).addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/assets/*");




    }

    private DefaultPluginManager<ReststopPlugin> buildPluginManager(ServletContext servletContext, DefaultReststopPluginManager reststopPluginManager) throws ServletException {

        DefaultReststop reststop = new DefaultReststop();

        final PluginDelegatingServiceLocator pluginDelegatingServiceLocator = new PluginDelegatingServiceLocator();
        DefaultPluginManager<ReststopPlugin> manager = buildFor(ReststopPlugin.class)
                .withClassLoaderProvider(reststop)
                .withClassLoaderProviders(findClassLoaderProviders(servletContext))
                .withClassLoader(getClass().getClassLoader())
                .withPluginLoader(new ConstructorInjectionPluginLoader())
                .withService(ServiceKey.by(Reststop.class), reststop)
                .withService(ServiceKey.by(ServletContext.class), servletContext)
                .withService(ServiceKey.by(ReststopPluginManager.class), reststopPluginManager)
                .withServiceLocator(pluginDelegatingServiceLocator)
                .build();

        pluginDelegatingServiceLocator.setPluginManager(manager);

        reststop.setManager(manager);
        return manager;
    }

    private class PluginDelegatingServiceLocator implements ServiceLocator {
        private Map<ServiceKey, Object> services = new ConcurrentHashMap<>();

        private void setPluginManager(PluginManager<ReststopPlugin> pluginManager) {
            pluginManager.addPluginManagerListener(new PluginManagerListener<ReststopPlugin>() {
                @Override
                public void beforeActivation(PluginManager<ReststopPlugin> pluginManager, ClassLoaderProvider classLoaderProvider, ClassLoader classLoader, PluginLoader<ReststopPlugin> pluginLoader, Collection<ReststopPlugin> plugins) {
                    for (ReststopPlugin plugin : plugins) {
                        for (Class<?> clazz : plugin.getServiceTypes()) {
                            services.put(ServiceKey.by(clazz), plugin.getService(clazz));
                        }
                    }
                }

                @Override
                public void afterPassivation(PluginManager<ReststopPlugin> pluginManager, ClassLoaderProvider classLoaderProvider, ClassLoader classLoader, PluginLoader<ReststopPlugin> pluginLoader, Collection<ReststopPlugin> plugins) {
                    for (ReststopPlugin plugin : plugins) {
                        for (Class<?> clazz : plugin.getServiceTypes()) {
                            services.remove(ServiceKey.by(clazz));
                        }
                    }
                }
            });

        }

        @Override
        public Set<ServiceKey> keySet() {
            return services.keySet();
        }

        @Override
        public <T> T get(ServiceKey<T> serviceKey) {
            return serviceKey.getType().cast(services.get(serviceKey));
        }

    }

    private ClassLoaderProvider[] findClassLoaderProviders(ServletContext servletContext) throws ServletException {
        List<ClassLoaderProvider> providers = new ArrayList<>();

        {
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
                providers.add(new PluginLinesClassLoaderProvider(pluginsXml, repoDir));
            }
        }
        return providers.toArray(new ClassLoaderProvider[providers.size()]);

    }



    private class DefaultReststop implements Reststop, ClassLoaderProvider {
        private Registry registry;
        private ClassLoader parentClassLoader;
        private DefaultPluginManager<ReststopPlugin> manager;

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
            return new MappingWrappedFilter(filter, mapping, phase);
        }

        @Override
        public Filter createServletFilter(HttpServlet servlet, String path) {
            return createFilter(new ServletWrapperFilter(servlet, path), path, FilterPhase.USER);
        }

        public void setManager(DefaultPluginManager<ReststopPlugin> manager) {
            this.manager = manager;
        }

        @Override
        public ServletConfig createServletConfig(String name, Properties properties) {
            return new PropertiesWebConfig(name, properties);
        }

        @Override
        public FilterConfig createFilterConfig(String name, Properties properties) {
            return new PropertiesWebConfig(name, properties);
        }

        @Override
        public FilterChain newFilterChain(FilterChain filterChain) {

            PluginFilterChain orig = (PluginFilterChain) filterChain;
            return buildFilterChain(orig.getFilterChain(), manager);
        }

        private class DefaultClassLoaderChange implements PluginClassLoaderChange {
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
                registry.replace(removes, adds);
            }
        }

        private class PropertiesWebConfig implements ServletConfig, FilterConfig  {
            private final String name;
            private final Properties properties;

            public PropertiesWebConfig(String name, Properties properties) {
                this.name = name;
                this.properties = properties;
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

        private class ServletWrapperFilter implements Filter {
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
                        return requestURI.substring(servletPath.length());
                    }
                }, resp);

            }

            @Override
            public void destroy() {

            }
        }
    }

    private class MappingWrappedFilter implements Filter {
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

            if(mappingMatchesRequest(req, mapping)) {
                filter.doFilter(servletRequest, servletResponse, filterChain);
            } else {
                filterChain.doFilter(servletRequest, servletResponse);
            }
        }

        private boolean mappingMatchesRequest(HttpServletRequest req, String mapping) {
            return mapping.equals(req.getRequestURI()) || mapping.endsWith("*") && req.getRequestURI().regionMatches(0, mapping, 0, mapping.length()-1);
        }



        @Override
        public void destroy() {

        }
    }

    private class PluginDelegatingFilter implements Filter {
        private final PluginManager<ReststopPlugin> manager;

        public PluginDelegatingFilter(PluginManager<ReststopPlugin> manager) {
            this.manager = manager;
        }

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {

        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

            servletResponse.setCharacterEncoding("utf-8");
            buildFilterChain(filterChain, manager).doFilter(servletRequest, servletResponse);
        }



        @Override
        public void destroy() {

        }
    }

    private class ClassLoaderFilter {
        final ClassLoader classLoader;
        final Filter filter;

        private ClassLoaderFilter(ClassLoader classLoader, Filter filter) {
            this.classLoader = classLoader;
            this.filter = filter;
        }
    }
    private FilterChain buildFilterChain(FilterChain filterChain, PluginManager<ReststopPlugin> pluginManager) {
        List<ClassLoaderFilter> filters = new ArrayList<>();
        for(ReststopPlugin plugin : pluginManager.getPlugins()) {
            for (Filter filter : plugin.getServletFilters()) {
                filters.add(new ClassLoaderFilter(pluginManager.getClassLoader(plugin), filter));
            }
        }

        Collections.sort(filters, new Comparator<ClassLoaderFilter>() {
            @Override
            public int compare(ClassLoaderFilter o1, ClassLoaderFilter o2) {
                FilterPhase phase1 = o1.filter instanceof MappingWrappedFilter ? ((MappingWrappedFilter)o1.filter).phase : FilterPhase.USER;
                FilterPhase phase2 = o2.filter instanceof MappingWrappedFilter ? ((MappingWrappedFilter)o2.filter).phase : FilterPhase.USER;
                return phase1.ordinal() - phase2.ordinal();
            }
        });
        return new PluginFilterChain(filters, filterChain);
    }
    private static class PluginFilterChain implements FilterChain {
        private final List<ClassLoaderFilter> filters;
        private final FilterChain filterChain;
        private int filterIndex;

        public PluginFilterChain(List<ClassLoaderFilter> filters, FilterChain filterChain) {
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
    }

    private class PluginLinesClassLoaderProvider implements ClassLoaderProvider {
        private final Document pluginsXml;
        private final File repoDir;

        public PluginLinesClassLoaderProvider(Document pluginsXml, File repoDir) {
            this.pluginsXml = pluginsXml;
            this.repoDir = repoDir;
        }

        @Override
        public void start(Registry registry, ClassLoader parentClassLoader) {
            List<ClassLoader> loaders = new ArrayList<>();
            Map<String, ClassLoader> byDep  = new HashMap<>();


            List<PluginInfo> infos = PluginInfo.sortByRuntimeDependencies(PluginInfo.parse(pluginsXml));

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

        private ClassLoader getParentClassLoader(PluginInfo pluginInfo, ClassLoader parentClassLoader, Map<String, ClassLoader> byDep) {
            Set<ClassLoader> delegates = new HashSet<ClassLoader>();

            for (Artifact dep : pluginInfo.getDependsOn()) {
                ClassLoader dependencyLoader = byDep.get(dep.getGroupIdAndArtifactId());
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

    private class AssetFilter implements Filter {
        private final DefaultReststopPluginManager manager;

        public AssetFilter(DefaultReststopPluginManager manager) {
            this.manager = manager;
        }

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {

        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

            HttpServletRequest req = (HttpServletRequest) servletRequest;
            HttpServletResponse resp = (HttpServletResponse) servletResponse;

            for(ClassLoader loader : manager.getPluginClassLoaders()) {
                String requestURI = req.getRequestURI();

                String path = requestURI.substring("/assets/".length());

                InputStream stream = loader.getResourceAsStream("assets/" + path);
                if(stream != null) {
                    String mimeType = req.getServletContext().getMimeType(path.substring(path.lastIndexOf("/") + 1));
                    if(mimeType != null) {
                        resp.setContentType(mimeType);
                    }

                    copy(stream, servletResponse.getOutputStream());

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

    private class DefaultReststopPluginManager implements ReststopPluginManager{
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

                private ThreadLocal<ClassLoader> classLoader = new ThreadLocal<ClassLoader>();

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
}
