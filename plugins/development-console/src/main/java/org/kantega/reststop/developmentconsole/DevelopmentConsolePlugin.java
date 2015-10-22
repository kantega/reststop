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

package org.kantega.reststop.developmentconsole;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.kantega.reststop.api.*;
import org.kantega.reststop.classloaderutils.PluginClassLoader;
import org.kantega.reststop.classloaderutils.PluginInfo;

import javax.servlet.*;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.*;

/**
 *
 */
@Plugin
public class DevelopmentConsolePlugin {


    private final ReststopPluginManager pluginManager;
    private final VelocityEngine velocityEngine;

    @Export
    private final Filter devConsole;

    @Export
    private final Filter redirect;

    public DevelopmentConsolePlugin(ServletBuilder servletBuilder, ReststopPluginManager pluginManager, VelocityEngine velocityEngine) {
        this.pluginManager = pluginManager;
        this.velocityEngine = velocityEngine;

        devConsole = servletBuilder.filter(new DevelopentConsole(), "/dev/", FilterPhase.PRE_UNMARSHAL);
        redirect = servletBuilder.redirectServlet("/dev", "dev/");
    }

    public class DevelopentConsole implements Filter {


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
            context.put("dateTool", new DateTool());
            context.put("obfTool", new ObfTool());
            context.put("consoleTool", new ConsoleTool());


            context.put("pluginInfos", getPluginInfos(pluginManager));
            velocityEngine.getTemplate("templates/console.vm").merge(context, resp.getWriter());
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

        private Map<ClassLoader, Collection<Object>> getPluginClassLoaders(ReststopPluginManager pluginManager) {
            Map<ClassLoader, Collection<Object>> map = new IdentityHashMap<>();

            Map<PluginInfo, ClassLoader> infos = new IdentityHashMap<>();

            for (ClassLoader classLoader : pluginManager.getPluginClassLoaders()) {
                if ( classLoader instanceof PluginClassLoader && !map.containsKey(classLoader)) {
                    map.put(classLoader, new ArrayList<>());
                    infos.put(((PluginClassLoader) classLoader).getPluginInfo(), classLoader);
                }
            }
            for (Object plugin : pluginManager.getPlugins()) {
                map.get(pluginManager.getClassLoader(plugin)).add(plugin);
            }

            List<PluginInfo> sorted = PluginInfo.resolveStartupOrder(new ArrayList<>(infos.keySet()));

            Map<ClassLoader, Collection<Object>> map2 = new LinkedHashMap<>();

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
}
