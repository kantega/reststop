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

import com.google.common.io.Files;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature;
import org.glassfish.jersey.servlet.ServletContainer;
import org.kantega.jexmec.ClassLoaderProvider;
import org.kantega.jexmec.PluginManager;
import org.kantega.jexmec.PluginManagerListener;
import org.kantega.jexmec.ServiceKey;
import org.kantega.jexmec.ctor.ConstructorInjectionPluginLoader;
import org.kantega.jexmec.manager.DefaultPluginManager;
import org.kantega.reststop.api.PluginListener;
import org.kantega.reststop.api.Reststop;
import org.kantega.reststop.api.ReststopPlugin;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.Application;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.Charset;
import java.util.*;

import static org.kantega.jexmec.manager.DefaultPluginManager.buildFor;

/**
 *
 */
public class ReststopInitializer implements ServletContainerInitializer{

    private ServletContainer container;

    @Override
    public void onStartup(Set<Class<?>> classes, ServletContext servletContext) throws ServletException {


        final DefaultPluginManager<ReststopPlugin> manager = buildPluginManager(servletContext);

        manager.addPluginManagerListener(new PluginManagerListener<ReststopPlugin>() {
            @Override
            public void pluginsUpdated(Collection<ReststopPlugin> plugins) {
                if(container != null) {
                    container.reload(getResourceConfig(new ReststopApplication(manager)));
                }
            }
        });
        manager.addPluginManagerListener(new PluginManagerListener<ReststopPlugin>() {
            @Override
            public void afterPluginManagerStarted(PluginManager pluginManager) {
                PluginManager<ReststopPlugin> pm = pluginManager;
                for(ReststopPlugin plugin : pm.getPlugins()) {
                    for(PluginListener listener : plugin.getPluginListeners()) {
                        listener.pluginManagerStarted();
                    }
                }
            }
        });
        manager.start();

        servletContext.addFilter(PluginDelegatingFilter.class.getName(), new PluginDelegatingFilter(manager))
                .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");


        container = addJerseyFilter(servletContext, new ReststopApplication(manager));


    }

    private DefaultPluginManager<ReststopPlugin> buildPluginManager(ServletContext servletContext) throws ServletException {

        DefaultReststop reststop = new DefaultReststop();
        DefaultPluginManager<ReststopPlugin> build = buildFor(ReststopPlugin.class)
                        .withClassLoaderProvider(reststop)
                        .withClassLoaderProviders(findClassLoaderProviders(servletContext))
                        .withClassLoader(getClass().getClassLoader())
                        .withPluginLoader(new ConstructorInjectionPluginLoader())
                        .withService(ServiceKey.by(Reststop.class), reststop)
                        .withService(ServiceKey.by(ServletContext.class), servletContext)
                        .build();



        return build;
    }

    private ClassLoaderProvider[] findClassLoaderProviders(ServletContext servletContext) throws ServletException {
        List<ClassLoaderProvider> providers = new ArrayList<ClassLoaderProvider>();

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
            List<String> pluginsLines = (List<String>) servletContext.getAttribute("pluginsList");
            if(pluginsLines != null) {
                providers.add(new PluginLinesClassLoaderProvider(pluginsLines));
            }
        }
        return providers.toArray(new ClassLoaderProvider[providers.size()]);

    }

    private ServletContainer addJerseyFilter(ServletContext servletContext, Application application) {
        ResourceConfig resourceConfig = getResourceConfig(application);
        ServletContainer container = new ServletContainer(resourceConfig);

        servletContext.addFilter("jersey", container)
        .addMappingForUrlPatterns(EnumSet.of(DispatcherType.REQUEST), true, "/*");

        return container;
    }

    private ResourceConfig getResourceConfig(Application application) {
        ResourceConfig resourceConfig = ResourceConfig.forApplication(application);
        resourceConfig.register(RolesAllowedDynamicFeature.class);
        return resourceConfig;
    }

    private class DefaultReststop implements Reststop, ClassLoaderProvider {
        private Registry registry;
        private ClassLoader parentClassLoader;

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
        public Filter createFilter(Filter filter, String mapping) {
            return new MappingWrappedFilter(filter, mapping);
        }

        private class DefaultClassLoaderChange implements PluginClassLoaderChange {
            private final Registry registry;
            private List<ClassLoader> adds = new ArrayList<>();
            private List<ClassLoader> removes = new ArrayList<>();

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

        public MappingWrappedFilter(Filter filter, String mapping) {
            this.filter = filter;
            this.mapping = mapping;
        }

        @Override
        public void init(FilterConfig filterConfig) throws ServletException {

        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
            HttpServletRequest req = (HttpServletRequest) servletRequest;

            if(mapping.endsWith("*") && req.getRequestURI().regionMatches(0, mapping, 0, mapping.length()-1)) {
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

            buildFilterChain(filterChain).doFilter(servletRequest, servletResponse);
        }

        private FilterChain buildFilterChain(FilterChain filterChain) {
            List<Filter> filters = new ArrayList<>();
            for(ReststopPlugin plugin : manager.getPlugins()) {
                filters.addAll(plugin.getServletFilters());
            }
            return new PluginFilterChain(filters, filterChain);
        }

        @Override
        public void destroy() {

        }
    }
    private static class PluginFilterChain implements FilterChain {
        private final List<Filter> filters;
        private FilterChain filterChain;
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
                List<String> lines = Files.readLines(pluginsTxt, Charset.forName("utf-8"));
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
            super.addURL(url);    //To change body of overridden methods use File | Settings | File Templates.
        }
    }

    private class PluginLinesClassLoaderProvider implements ClassLoaderProvider {
        private final List<String> pluginsLines;

        public PluginLinesClassLoaderProvider(List<String> pluginsLines) {
            this.pluginsLines = pluginsLines;
        }

        @Override
        public void start(Registry registry, ClassLoader parentClassLoader) {
            ArrayList<ClassLoader> loaders = new ArrayList<>();
            for(String line : pluginsLines) {
                PluginClassloader pluginClassloader = new PluginClassloader(parentClassLoader);
                for(String path : line.split(":")) {

                    try {
                        pluginClassloader.addURL(new File(path).toURI().toURL());
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    }
                }

                loaders.add(pluginClassloader);
            }

            registry.add(loaders);
        }

        @Override
        public void stop() {

        }
    }
}
