package org.kantega.reststop.maven;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.kantega.reststop.classloaderutils.Artifact;
import org.kantega.reststop.classloaderutils.PluginInfo;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

/**
 *
 */
public class Deployer {

    private final Resolver resolver;
    private final Log log;

    public Deployer(Resolver resolver, Log log) {
        this.resolver = resolver;
        this.log = log;
    }

    public void deployPlugin(String groupId, String artifactId, String version, File basedir) throws MojoFailureException, MojoExecutionException {
        org.kantega.reststop.maven.Plugin plugin = new org.kantega.reststop.maven.Plugin(groupId, artifactId, version);

        PluginInfo resolved = resolver.resolve(plugin);

        sendDeployPost(resolved, basedir);
    }

    private void sendDeployPost(PluginInfo resolved, File basedir) throws MojoExecutionException {
        try {
            HttpURLConnection con = (HttpURLConnection) new URL("http://localhost:8080/dev/deploy").openConnection();
            con.setDoOutput(true);
            con.setRequestMethod("POST");
            con.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
            try(OutputStream out = con.getOutputStream()) {
                out.write(getDeployParams(resolved, basedir));
            }
            String s = IOUtils.toString(con.getInputStream());
            log.info("Server said: " + con.getResponseCode() +": " + s);
        } catch (IOException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }
    }

    private byte[] getDeployParams(PluginInfo resolved, File basedir) {
        StringBuilder sb = new StringBuilder();

        addParam(sb, "groupId", resolved.getGroupId());
        addParam(sb, "artifactId", resolved.getArtifactId());
        addParam(sb, "version", resolved.getVersion());
        addParam(sb, "basedir", basedir.getAbsolutePath());
        for(String scope : asList(JavaScopes.TEST, JavaScopes.RUNTIME, JavaScopes.COMPILE)) {
            addParam(sb, scope, getClassPath(resolved, scope));
        }

        try {
            return sb.toString().getBytes("utf-8");
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException(e);
        }
    }

    private String getClassPath(PluginInfo pluginInfo, String scope) {

        StringBuilder sb = new StringBuilder();

        for (Artifact artifact : pluginInfo.getClassPath(scope)) {
            if(sb.length() > 0) {
                sb.append(";");
            }
            sb.append(Stream.of(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), artifact.getFile().getAbsolutePath())
                    .collect(Collectors.joining(":")));
        }
        return sb.toString();
    }

    private void addParam(StringBuilder sb, String name, String value) {
        if(sb.length()> 0) {
            sb.append("&");
        }
        sb.append(name).append("=").append(value);
    }
}
