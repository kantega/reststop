package org.kantega.reststop.custom.web;

import org.kantega.jexmec.PluginManager;
import org.kantega.reststop.api.ReststopPlugin;
import org.kantega.reststop.custom.api.CustomAppPlugin;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 *
 */
public class GreetingsServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

        PluginManager<ReststopPlugin> manager = (PluginManager<ReststopPlugin>) request.getServletContext ().getAttribute("reststopPluginManager");

        List<String> greetings = new ArrayList<>();

        for (CustomAppPlugin customAppPlugin : manager.getPlugins(CustomAppPlugin.class)) {
            greetings.addAll(customAppPlugin.getGreetings());
        }

        request.setAttribute("greetings", greetings);

        request.setAttribute("appPlugins", manager.getPlugins(CustomAppPlugin.class));
        Collection<ReststopPlugin> all = manager.getPlugins();
        List<ReststopPlugin> reststopPlugins = new ArrayList<>();
        for (ReststopPlugin plugin : all) {
            if(!(plugin instanceof CustomAppPlugin)) {
                reststopPlugins.add(plugin);
            }
        }
        request.setAttribute("reststopPlugins", reststopPlugins);

        request.getRequestDispatcher("/WEB-INF/jsp/greetings.jsp").forward(request, response);
    }
}
