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
import org.kantega.reststop.api.Export;
import org.kantega.reststop.api.Plugin;
import org.kantega.reststop.api.ReststopPluginManager;
import org.kantega.reststop.classloaderutils.PluginClassLoader;
import org.kantega.reststop.classloaderutils.PluginInfo;
import org.kantega.reststop.servlet.api.FilterPhase;
import org.kantega.reststop.servlet.api.ServletBuilder;

import javax.servlet.*;
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
    private final Collection<PluginClassLoader> classLoaders;

    public DevelopmentConsolePlugin(ServletBuilder servletBuilder, Collection<PluginClassLoader> classLoaders, ReststopPluginManager pluginManager, VelocityEngine velocityEngine) {
        this.classLoaders = classLoaders;
        this.pluginManager = pluginManager;
        this.velocityEngine = velocityEngine;

        devConsole = servletBuilder.filter(new DevelopentConsole(), FilterPhase.PRE_UNMARSHAL, "/dev/");
        redirect = servletBuilder.redirectFrom("/dev").to("dev/");
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
            context.put("pluginClassloaders", getPluginClassLoaders());
            context.put("dateTool", new DateTool());
            context.put("obfTool", new ObfTool());
            context.put("consoleTool", new ConsoleTool());


            context.put("pluginInfos", getPluginInfos());
            velocityEngine.getTemplate("templates/console.vm").merge(context, resp.getWriter());
        }

        private List<PluginInfo> getPluginInfos() {
            List<PluginInfo> infos = new ArrayList<>();
            for (PluginClassLoader classLoader : classLoaders) {
                infos.add(classLoader.getPluginInfo());
            }
            return infos;
        }

        private Map<ClassLoader, Collection<Object>> getPluginClassLoaders() {
            Map<ClassLoader, Collection<Object>> map = new IdentityHashMap<>();

            Map<PluginInfo, ClassLoader> infos = new IdentityHashMap<>();

            for (ClassLoader classLoader : classLoaders) {
                if ( classLoader instanceof PluginClassLoader && !map.containsKey(classLoader)) {
                    map.put(classLoader, new ArrayList<>());
                    infos.put(((PluginClassLoader) classLoader).getPluginInfo(), classLoader);
                }
            }
            for (Object plugin : pluginManager.getPlugins()) {
                map.get(pluginManager.getClassLoader(plugin)).add(plugin);
            }

            List<PluginInfo> sorted = PluginInfo.resolveClassloaderOrder(new ArrayList<>(infos.keySet()));

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
