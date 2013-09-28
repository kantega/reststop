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

import org.kantega.jexmec.ClassLoaderProvider;
import org.kantega.jexmec.PluginManager;
import org.kantega.jexmec.PluginManagerListener;
import org.kantega.jexmec.ServiceKey;
import org.kantega.jexmec.ctor.ConstructorInjectionPluginLoader;
import org.kantega.jexmec.manager.DefaultPluginManager;
import org.kantega.reststop.api.*;
import org.kantega.reststop.classloaderutils.*;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.util.*;

import static org.kantega.jexmec.manager.DefaultPluginManager.buildFor;

/**
 *
 */
public class ReststopInitializer implements ServletContainerInitializer{


    private boolean pluginManagerStarted;

    @Override
    public void onStartup(Set<Class<?>> classes, ServletContext servletContext) throws ServletException {


        final DefaultPluginManager<ReststopPlugin> manager = buildPluginManager(servletContext);

        servletContext.setAttribute("reststopPluginManager", manager);

        manager.addPluginManagerListener(new PluginManagerListener<ReststopPlugin>() {
            @Override
            public void pluginsUpdated(Collection<ReststopPlugin> plugins) {
                if (pluginManagerStarted) {
                    for (ReststopPlugin plugin : manager.getPlugins()) {
                        for (PluginListener listener : plugin.getPluginListeners()) {
                            listener.pluginsUpdated(plugins);
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
                    for(PluginListener listener : plugin.getPluginListeners()) {
                        listener.pluginManagerStarted();
                    }
                }
                for (ReststopPlugin plugin : manager.getPlugins()) {
                    for (PluginListener listener : plugin.getPluginListeners()) {
                        listener.pluginsUpdated(plugins);
                    }
                }
            }
        });
        manager.start();

        servletContext.addFilter(PluginDelegatingFilter.class.getName(), new PluginDelegatingFilter(manager))
                .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");

        servletContext.addFilter(AssetFilter.class.getName(), new AssetFilter(manager)).addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/assets/*");




    }

    private DefaultPluginManager<ReststopPlugin> buildPluginManager(ServletContext servletContext) throws ServletException {

        DefaultReststop reststop = new DefaultReststop();

        DefaultReststopPluginManager reststopPluginManager = new DefaultReststopPluginManager();

        DefaultPluginManager<ReststopPlugin> manager = buildFor(ReststopPlugin.class)
                .withClassLoaderProvider(reststop)
                .withClassLoaderProviders(findClassLoaderProviders(servletContext))
                .withClassLoader(getClass().getClassLoader())
                .withPluginLoader(new ConstructorInjectionPluginLoader())
                .withService(ServiceKey.by(Reststop.class), reststop)
                .withService(ServiceKey.by(ServletContext.class), servletContext)
                .withService(ServiceKey.by(ReststopPluginManager.class), reststopPluginManager)
                .build();

        reststopPluginManager.setManager(manager);
        reststop.setManager(manager);
        return manager;
    }

    private ClassLoaderProvider[] findClassLoaderProviders(ServletContext servletContext) throws ServletException {
        List<ClassLoaderProvider> providers = new ArrayList<>();

        {
            Document pluginsXml = (Document) servletContext.getAttribute("pluginsXml");
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
                providers.add(new PluginLinesClassLoaderProvider(pluginsXml));
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

        public void setManager(DefaultPluginManager<ReststopPlugin> manager) {
            this.manager = manager;
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

            if(mapping.equals(req.getRequestURI()) || mapping.endsWith("*") && req.getRequestURI().regionMatches(0, mapping, 0, mapping.length()-1)) {
                filter.doFilter(servletRequest, servletResponse, filterChain);
            } else {
                filterChain.doFilter(servletRequest, servletResponse);
            }
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

            buildFilterChain(filterChain, manager).doFilter(servletRequest, servletResponse);
        }



        @Override
        public void destroy() {

        }
    }

    private FilterChain buildFilterChain(FilterChain filterChain, PluginManager<ReststopPlugin> pluginManager) {
        List<Filter> filters = new ArrayList<>();
        for(ReststopPlugin plugin : pluginManager.getPlugins()) {
            filters.addAll(plugin.getServletFilters());
        }

        Collections.sort(filters, new Comparator<Filter>() {
            @Override
            public int compare(Filter o1, Filter o2) {
                FilterPhase phase1 = o1 instanceof MappingWrappedFilter ? ((MappingWrappedFilter)o1).phase : FilterPhase.USER;
                FilterPhase phase2 = o2 instanceof MappingWrappedFilter ? ((MappingWrappedFilter)o2).phase : FilterPhase.USER;
                return phase1.ordinal() - phase2.ordinal();
            }
        });
        return new PluginFilterChain(filters, filterChain);
    }
    private static class PluginFilterChain implements FilterChain {
        private final List<Filter> filters;
        private final FilterChain filterChain;
        private int filterIndex;

        public PluginFilterChain(List<Filter> filters, FilterChain filterChain) {
            this.filters = filters;
            this.filterChain = filterChain;
        }
        public void doFilter(ServletRequest request, ServletResponse response) throws IOException, ServletException {
            if(filterIndex == filters.size()) {
                filterChain.doFilter(request, response);
            } else {
                filters.get(filterIndex++).doFilter(request, response, this);
            }
        }

        private FilterChain getFilterChain() {
            return filterChain;
        }
    }

    private class PluginLinesClassLoaderProvider implements ClassLoaderProvider {
        private final Document pluginsXml;

        public PluginLinesClassLoaderProvider(Document pluginsXml) {
            this.pluginsXml = pluginsXml;
        }

        @Override
        public void start(Registry registry, ClassLoader parentClassLoader) {
            List<ClassLoader> loaders = new ArrayList<>();
            Map<String, ClassLoader> byDep  = new HashMap<>();


            List<PluginInfo> infos = PluginInfo.sortByRuntimeDependencies(PluginInfo.parse(pluginsXml));

            for (PluginInfo info : infos) {

                if(info.isDirectDeploy()) {
                    PluginClassLoader pluginClassloader = new PluginClassLoader(info, getParentClassLoader(info, parentClassLoader, byDep));

                    File pluginJar = info.getPluginFile();

                    try {
                        pluginClassloader.addURL(pluginJar.toURI().toURL());

                        for (File file : info.getClassPathFiles("runtime")) {
                            pluginClassloader.addURL(file.toURI().toURL());

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

        private ClassLoader getParentClassLoader(PluginInfo pluginInfo, ClassLoader parentClassLoader, Map<String, ClassLoader> byDep) {
            Set<ClassLoader> delegates = new HashSet<ClassLoader>();

            for (Artifact dep : pluginInfo.getClassPath("compile")) {
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
        private final PluginManager<ReststopPlugin> manager;

        public AssetFilter(PluginManager<ReststopPlugin> manager) {
            this.manager = manager;
        }

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {

        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

            HttpServletRequest req = (HttpServletRequest) servletRequest;
            HttpServletResponse resp = (HttpServletResponse) servletResponse;

            for(ReststopPlugin plugin : manager.getPlugins()) {
                ClassLoader loader = manager.getClassLoader(plugin);
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
        }
    }
}
