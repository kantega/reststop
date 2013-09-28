package org.kantega.reststop.developmentconsole;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.kantega.reststop.api.*;
import org.kantega.reststop.classloaderutils.PluginClassLoader;
import org.kantega.reststop.classloaderutils.PluginInfo;
import org.kantega.reststop.development.DevelopmentPlugin;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

/**
 *
 */
public class DevelopmentConsolePlugin extends DefaultReststopPlugin {


    private final ReststopPluginManager pluginManager;

    public DevelopmentConsolePlugin(Reststop reststop, ReststopPluginManager pluginManager) {
        this.pluginManager = pluginManager;
        addServletFilter(reststop.createFilter(new DeveloperConsole(), "/dev*", FilterPhase.USER));
    }

    public class DeveloperConsole implements Filter {


        @Override
        public void init(FilterConfig filterConfig) throws ServletException {

        }

        @Override
        public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {

            HttpServletRequest req = (HttpServletRequest) servletRequest;

            HttpServletResponse resp = (HttpServletResponse) servletResponse;

            resp.setContentType("text/html");

            VelocityContext context = new VelocityContext();
            context.put("contextPath", req.getContextPath());
            context.put("pluginClassloaders", getPluginClassLoaders(pluginManager));

            context.put("pluginInfos", getPluginInfos(pluginManager));
            getEngine().getTemplate("templates/console.vm").merge(context, resp.getWriter());
        }

        private List<PluginInfo> getPluginInfos(ReststopPluginManager pluginManager) {
            List<PluginInfo> infos = new ArrayList<>();
            for (ClassLoader classLoader : pluginManager.getPluginClassLoaders()) {
                if(classLoader instanceof PluginClassLoader) {
                    PluginClassLoader loader = (PluginClassLoader) classLoader;
                    infos.add(loader.getPluginInfo());
                }
            }
            return infos;
        }

        private Map<ClassLoader, Collection<ReststopPlugin>> getPluginClassLoaders(ReststopPluginManager pluginManager) {
            Map<ClassLoader, Collection<ReststopPlugin>> map = new IdentityHashMap<>();

            Map<PluginInfo, ClassLoader> infos = new IdentityHashMap<>();

            for (ClassLoader classLoader : pluginManager.getPluginClassLoaders()) {
                if ( classLoader instanceof PluginClassLoader && !map.containsKey(classLoader)) {
                    map.put(classLoader, new ArrayList<ReststopPlugin>());
                    infos.put(((PluginClassLoader) classLoader).getPluginInfo(), classLoader);
                }
            }
            for (ReststopPlugin plugin : pluginManager.getPlugins()) {
                map.get(pluginManager.getClassLoader(plugin)).add(plugin);
            }

            List<PluginInfo> sorted = PluginInfo.sortByRuntimeDependencies(new ArrayList<>(infos.keySet()));

            Map<ClassLoader, Collection<ReststopPlugin>> map2 = new LinkedHashMap<>();

            for (PluginInfo info : sorted) {
                ClassLoader classLoader = infos.get(info);
                map2.put(classLoader, map.get(classLoader));
            }



            return map2;
        }


        @Override
        public void destroy() {

        }
    }

    private VelocityEngine getEngine() {
       return pluginManager.getPlugins(DevelopmentPlugin.class).iterator().next().getVelocityEngine();
    }
}
