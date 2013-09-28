package org.kantega.reststop.developmentconsole;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.kantega.reststop.api.*;
import org.kantega.reststop.development.DevelopmentPlugin;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Map;

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
            context.put("plugins", pluginManager.getPlugins());
            context.put("pluginClassloaders", getPluginClassLoaders(pluginManager));
            getEngine().getTemplate("templates/console.vm").merge(context, resp.getWriter());
        }

        private Map<ClassLoader, Collection<ReststopPlugin>> getPluginClassLoaders(ReststopPluginManager pluginManager) {
            Map<ClassLoader, Collection<ReststopPlugin>> map = new IdentityHashMap<>();

            for (ReststopPlugin plugin : pluginManager.getPlugins()) {
                ClassLoader classLoader = pluginManager.getClassLoader(plugin);
                if (!map.containsKey(classLoader)) {
                    map.put(classLoader, new ArrayList<ReststopPlugin>());
                }
                Collection<ReststopPlugin> reststopPlugins = map.get(classLoader);
                reststopPlugins.add(plugin);
            }

            return map;
        }


        @Override
        public void destroy() {

        }
    }

    private VelocityEngine getEngine() {
       return pluginManager.getPlugins(DevelopmentPlugin.class).iterator().next().getVelocityEngine();
    }
}
