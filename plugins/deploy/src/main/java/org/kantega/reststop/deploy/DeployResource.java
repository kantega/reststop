package org.kantega.reststop.deploy;

import org.kantega.reststop.classloaderutils.PluginInfo;
import org.kantega.reststop.deploy.model.Plugin;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
@Path("mavenDeploy")
public class DeployResource {
    private final MavenApi mavenApi;
    private final DeployService deployService;

    public DeployResource(MavenApi mavenApi, DeployService deployService) {
        this.mavenApi = mavenApi;
        this.deployService = deployService;
    }


    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public List<Plugin> list() {
        return new ArrayList<>(deployService.deployedPlugins().values());
    }

    @POST
    @Path("deploy")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response deploy(Plugin plugin) {
        PluginInfo pluginInfo = mavenApi.resolvePlugin(plugin);
        deployService.deploy(pluginInfo);
        return Response.ok().build();
    }

    @POST
    @Path("undeploy")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response undeploy(Plugin plugin) {
        deployService.undeploy(plugin);
        return Response.ok().build();
    }

    @POST
    @Path("start")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response start(Plugin plugin) {
        PluginInfo pluginInfo = mavenApi.resolvePlugin(plugin);
        deployService.start(pluginInfo);
        return Response.ok().build();
    }

    @POST
    @Path("stop")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response stop(Plugin plugin) {
        deployService.stop(plugin);
        return Response.ok().build();
    }
}
