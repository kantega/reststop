package org.kantega.reststop.maven;

import org.apache.maven.model.Exclusion;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.graph.DependencyFilter;
import org.eclipse.aether.graph.DependencyNode;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.kantega.reststop.classloaderutils.PluginInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static java.util.Arrays.asList;

/**
 *
 */
public class Resolver {
    private final RepositorySystem repoSystem;
    private final RepositorySystemSession repoSession;
    private final List<RemoteRepository> remoteRepos;
    private final Log log;

    public Resolver(RepositorySystem repoSystem, RepositorySystemSession repoSession, List<RemoteRepository> remoteRepos, Log log) {
        this.repoSystem = repoSystem;
        this.repoSession = repoSession;
        this.remoteRepos = remoteRepos;
        this.log = log;
    }

    public List<PluginInfo> resolve(List<Plugin> plugins) throws MojoFailureException, MojoExecutionException {
        List<PluginInfo> pluginInfos = new ArrayList<>();

        for (Plugin plugin : plugins) {
            pluginInfos.add(resolve(plugin));
        }

        return pluginInfos;
    }

    public PluginInfo resolve(Plugin plugin) throws MojoFailureException, MojoExecutionException {
        PluginInfo info = plugin.asPluginInfo();

        Artifact pluginArtifact = resolveArtifact(plugin.getCoords());

        info.setFile(pluginArtifact.getFile());

        try {
            ArtifactDescriptorResult descriptorResult = repoSystem.readArtifactDescriptor(repoSession, new ArtifactDescriptorRequest(pluginArtifact, remoteRepos, null));

            CollectRequest collectRequest = getCollectRequest(plugin, descriptorResult);


            resolveClasspaths(info, collectRequest);
        } catch (DependencyResolutionException | ArtifactDescriptorException e) {
            throw new MojoFailureException("Failed resolving plugin dependencies", e);
        }
        return info;
    }

    public void resolveClasspaths(PluginInfo info, CollectRequest collectRequest) throws DependencyResolutionException, MojoFailureException {
        for(String scope : asList(JavaScopes.TEST, JavaScopes.RUNTIME, JavaScopes.COMPILE)) {

            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, new TransitiveFilter(DependencyFilterUtils.classpathFilter(scope)));

            DependencyResult dependencyResult = repoSystem.resolveDependencies(repoSession, dependencyRequest);

            if(!dependencyResult.getCollectExceptions().isEmpty()) {
                throw new MojoFailureException("Failed resolving plugin dependencies", dependencyResult.getCollectExceptions().get(0));
            }

            for(ArtifactResult result : dependencyResult.getArtifactResults()) {
                Artifact artifact = result.getArtifact();
                org.kantega.reststop.classloaderutils.Artifact pa  = new org.kantega.reststop.classloaderutils.Artifact();
                info.getClassPath(scope).add(pa);

                pa.setGroupId(artifact.getGroupId());
                pa.setArtifactId(artifact.getArtifactId());
                pa.setVersion(artifact.getBaseVersion());

                pa.setFile(artifact.getFile());
            }

        }
    }

    private CollectRequest getCollectRequest(Plugin plugin, ArtifactDescriptorResult descriptorResult) {
        CollectRequest collectRequest = new CollectRequest();

        for (RemoteRepository repo : remoteRepos) {
            collectRequest.addRepository(repo);
        }
        for (org.eclipse.aether.graph.Dependency dependency : descriptorResult.getDependencies()) {
            collectRequest.addDependency(dependency);
        }

        collectRequest.setManagedDependencies(descriptorResult.getManagedDependencies());


        if(plugin.getDependencies() != null) {
            for (Dependency dependency : plugin.getDependencies()) {
                List<org.eclipse.aether.graph.Exclusion> exclusions = new ArrayList<>();

                if(dependency.getExclusions() != null) {
                    for (Exclusion exclusion : dependency.getExclusions()) {
                        exclusions.add(new org.eclipse.aether.graph.Exclusion(exclusion.getGroupId(), exclusion.getArtifactId(), "*", "*"));
                    }
                }
                org.eclipse.aether.graph.Dependency dep = new org.eclipse.aether.graph.Dependency(new DefaultArtifact(dependency.getGroupId(),
                        dependency.getArtifactId(), dependency.getClassifier(), dependency.getType(), dependency.getVersion()), dependency.getScope(), dependency.isOptional(), exclusions);


                collectRequest.addDependency(dep);

            }
        }
        return collectRequest;
    }

    public Artifact resolveArtifact(String coords) throws MojoFailureException, MojoExecutionException {
        Artifact artifact;
        try
        {
            artifact = new DefaultArtifact(coords);
        }
        catch ( IllegalArgumentException e )
        {
            throw new MojoFailureException( e.getMessage(), e );
        }

        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact( artifact );
        request.setRepositories( remoteRepos );

        log.info( "Resolving artifact " + artifact + " from " + remoteRepos );

        ArtifactResult result;
        try
        {
            result = repoSystem.resolveArtifact( repoSession, request );
        }
        catch ( ArtifactResolutionException e )
        {
            throw new MojoExecutionException( e.getMessage(), e );
        }

        log.info( "Resolved artifact " + artifact + " to " + result.getArtifact().getFile() + " from "
                + result.getRepository() );

        return result.getArtifact();
    }

    private class TransitiveFilter implements DependencyFilter {
        private final DependencyFilter dependencyFilter;

        public TransitiveFilter(DependencyFilter dependencyFilter) {
            this.dependencyFilter = dependencyFilter;
        }

        @Override
        public boolean accept(DependencyNode node, List<DependencyNode> parents) {
            for (DependencyNode parent : parents) {
                if(!dependencyFilter.accept(parent, Collections.emptyList())) {
                    return false;
                }
            }
            return dependencyFilter.accept(node, parents);
        }
    }
}
