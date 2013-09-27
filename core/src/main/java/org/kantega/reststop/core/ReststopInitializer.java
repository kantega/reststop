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

import org.apache.commons.io.IOUtils;
import org.kantega.jexmec.ClassLoaderProvider;
import org.kantega.jexmec.PluginManager;
import org.kantega.jexmec.PluginManagerListener;
import org.kantega.jexmec.ServiceKey;
import org.kantega.jexmec.ctor.ConstructorInjectionPluginLoader;
import org.kantega.jexmec.manager.DefaultPluginManager;
import org.kantega.reststop.api.*;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
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
            String pluginsTxtPath = servletContext.getInitParameter("plugins.txt");
            if(pluginsTxtPath != null) {
                File pluginsTxt = new File(pluginsTxtPath);
                if(!pluginsTxt.exists()) {
                    throw new ServletException("Path not found: " + pluginsTxt.getAbsolutePath());
                }
                providers.add(new PluginsTxtClassLoaderProvider(pluginsTxt));
            }
        }

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

    private class PluginsTxtClassLoaderProvider implements ClassLoaderProvider {

        private final File pluginsTxt;

        public PluginsTxtClassLoaderProvider(File pluginsTxt) {

            this.pluginsTxt = pluginsTxt;
        }

        @Override
        public void start(Registry registry, ClassLoader parentClassLoader) {

            File repository = new File(pluginsTxt.getParentFile(), "repository");

            List<ClassLoader> loaders = new ArrayList<>();


            try {
                List<String> lines = Files.readAllLines(pluginsTxt.toPath(), Charset.forName("utf-8"));
                for (String line : lines) {

                    PluginClassloader pluginClassloader = new PluginClassloader(parentClassLoader);
                    String[] coords = line.split(",");
                    for (String coord : coords) {
                        coord = coord.trim();
                        if(!coord.isEmpty()) {
                            pluginClassloader.addURL(new File(repository, toMavenPath(coord)).toURI().toURL());
                        }
                    }
                    loaders.add(pluginClassloader);

                }
                registry.add(loaders);

            } catch (IOException e) {
                try {
                    throw new ServletException("Can't read plugins descriptor " +pluginsTxt.getAbsolutePath());
                } catch (ServletException e1) {
                    throw new RuntimeException(e);
                }
            }
        }

        private String toMavenPath(String coord) {
            String[] gav = coord.split(":");
            return gav[0].replace('.','/')
                    +"/" +
                    gav[1] +
                    "/" +
                    gav[2] +"/" + gav[1] +"-" + gav[2] +".jar";
        }

        @Override
        public void stop() {

        }
    }

    public class PluginClassloader extends URLClassLoader {

        public PluginClassloader(ClassLoader parentClassLoader) {
            super(new URL[0], parentClassLoader);
        }

        @Override
        public void addURL(URL url) {
            super.addURL(url);
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


            NodeList pluginElements = pluginsXml.getDocumentElement().getElementsByTagName("plugin");
            for(int i = 0; i < pluginElements.getLength(); i++) {

                Element pluginElement = (Element) pluginElements.item(i);


                boolean directDeploy = !"false".equals(pluginElement.getAttribute("directDeploy"));

                if(directDeploy) {
                    PluginClassloader pluginClassloader = new PluginClassloader(parentClassLoader);

                    Element runtimeElement = (Element) pluginElement.getElementsByTagName("runtime").item(0);

                    NodeList artifacts = runtimeElement.getElementsByTagName("artifact");

                    File pluginJar = new File(pluginElement.getAttribute("pluginFile"));

                    try {
                        pluginClassloader.addURL(pluginJar.toURI().toURL());

                        for(int a = 0; a < artifacts.getLength(); a++) {
                            Element artifact = (Element) artifacts.item(a);
                            pluginClassloader.addURL(new File(artifact.getAttribute("file")).toURI().toURL());

                        }
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    }

                    loaders.add(pluginClassloader);

                }
            }

            registry.add(loaders);
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

                    IOUtils.copy(stream, servletResponse.getOutputStream());

                    return;
                }
            }

            filterChain.doFilter(servletRequest, servletResponse);
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
