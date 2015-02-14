package org.kantega.reststop.deploy;

import org.kantega.reststop.api.Config;
import org.kantega.reststop.api.Reststop;
import org.kantega.reststop.deploy.model.Plugin;
import org.kantega.reststop.jaxrsapi.DefaultJaxRsPlugin;

import java.io.File;
import java.net.URI;
import java.net.URISyntaxException;

/**
 *
 */
public class DeployPlugin extends DefaultJaxRsPlugin {


    private final DeployService deployService;
    private final MavenApi mavenApi;

    @Config
    private String mavenRepositories;

    @Config
    private String localMavenRepository;

    public DeployPlugin(final Reststop reststop) {
        final File localRepo = new File(localMavenRepository);
        mavenApi = new MavenApi(remoteRepos(), localRepo);
        deployService = new DeployService(localRepo, reststop);
        addJaxRsSingletonResource(new DeployResource(mavenApi, deployService));
    }

    @Override
    public void init() {
        for (Plugin plugin : deployService.deployedPlugins().values()) {
            deployService.start(mavenApi.resolvePlugin(plugin));
        }
    }

    private URI[] remoteRepos() {
        String[] split = mavenRepositories.split(",");
        URI[] uris = new URI[split.length];
        for (int i = 0; i < uris.length; i++) {
            try {
                uris[i] = new URI(split[i]);
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }

        }
        return uris;
    }

}
