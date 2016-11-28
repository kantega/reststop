package org.kantega.reststop.development;

import org.kantega.reststop.classloaderutils.Artifact;
import org.kantega.reststop.classloaderutils.PluginInfo;
import org.kantega.reststop.core.DefaultReststopPluginManager;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.Collections;

/**
 *
 */
public class RemoteDeployFilter implements Filter {
    private final DefaultReststopPluginManager pluginManager;

    public RemoteDeployFilter(DefaultReststopPluginManager pluginManager) {
        this.pluginManager = pluginManager;
    }

    @Override
    public void destroy() {

    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest req = (HttpServletRequest) request;

        PluginInfo info = new PluginInfo();
        info.setGroupId(req.getParameter("groupId"));
        info.setArtifactId(req.getParameter("artifactId"));
        info.setVersion(req.getParameter("version"));

        info.setSourceDirectory(new File(req.getParameter("basedir")));

        info.setFile(info.getSourceDirectory().toPath().resolve("target").resolve(info.getArtifactId() + "-" + info.getVersion() +".jar").toFile());

        parseClasspath(info, req, "runtime");
        parseClasspath(info, req, "test");
        parseClasspath(info, req, "compile");

        pluginManager.deploy(Collections.singletonList(info), DevelopmentClassLoaderFactory.getInstance());
    }

    private void parseClasspath(PluginInfo info, HttpServletRequest req, String scope) {
        String parameter = req.getParameter(scope);
        if(parameter != null) {
            for (String artifactPart : parameter.split(";")) {
                if(!artifactPart.isEmpty()) {
                    String[] parts = artifactPart.split(":");
                    info.getClassPath(scope).add(new Artifact(parts[0], parts[1], parts[2], new File(parts[3])));
                }
            }
        }
    }
}
