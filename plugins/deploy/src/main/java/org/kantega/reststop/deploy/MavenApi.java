package org.kantega.reststop.deploy;

import org.apache.maven.repository.internal.MavenRepositorySystemUtils;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.artifact.DefaultArtifact;
import org.eclipse.aether.collection.CollectRequest;
import org.eclipse.aether.connector.basic.BasicRepositoryConnectorFactory;
import org.eclipse.aether.graph.Dependency;
import org.eclipse.aether.impl.DefaultServiceLocator;
import org.eclipse.aether.internal.impl.DefaultChecksumPolicyProvider;
import org.eclipse.aether.internal.impl.DefaultRepositoryLayoutProvider;
import org.eclipse.aether.internal.impl.DefaultTransporterProvider;
import org.eclipse.aether.internal.impl.Maven2RepositoryLayoutFactory;
import org.eclipse.aether.repository.LocalRepository;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.repository.RepositoryPolicy;
import org.eclipse.aether.resolution.*;
import org.eclipse.aether.spi.connector.RepositoryConnectorFactory;
import org.eclipse.aether.spi.connector.checksum.ChecksumPolicyProvider;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutFactory;
import org.eclipse.aether.spi.connector.layout.RepositoryLayoutProvider;
import org.eclipse.aether.spi.connector.transport.TransporterFactory;
import org.eclipse.aether.spi.connector.transport.TransporterProvider;
import org.eclipse.aether.transport.file.FileTransporterFactory;
import org.eclipse.aether.transport.http.HttpTransporterFactory;
import org.eclipse.aether.util.artifact.JavaScopes;
import org.eclipse.aether.util.filter.DependencyFilterUtils;
import org.kantega.reststop.classloaderutils.PluginInfo;
import org.kantega.reststop.deploy.model.Plugin;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 *
 */
public class MavenApi {
    private final URI[] mavenRepositories;
    private final File localMavenRepository;
    private final RepositorySystem repositorySystem;
    private final RepositorySystemSession repositorySystemSession;

    public MavenApi(URI[] mavenRepositories, File localMavenRepository) {
        localMavenRepository.getParentFile().mkdirs();
        this.mavenRepositories = mavenRepositories;
        this.localMavenRepository = localMavenRepository;
        DefaultServiceLocator locator = MavenRepositorySystemUtils.newServiceLocator();

        locator.addService( RepositoryConnectorFactory.class, BasicRepositoryConnectorFactory.class );

        locator.addService(TransporterProvider.class,  DefaultTransporterProvider.class);

        locator.addService( TransporterFactory.class, HttpTransporterFactory.class );
        locator.addService( TransporterFactory.class, FileTransporterFactory.class );


        locator.addService(RepositoryLayoutProvider.class, DefaultRepositoryLayoutProvider.class);
        locator.addService(RepositoryLayoutFactory.class, Maven2RepositoryLayoutFactory.class);

        locator.addService(ChecksumPolicyProvider.class, DefaultChecksumPolicyProvider.class);

        locator.setErrorHandler( new DefaultServiceLocator.ErrorHandler()
        {
            @Override
            public void serviceCreationFailed( Class<?> type, Class<?> impl, Throwable exception )
            {
                exception.printStackTrace();
            }
        } );

        repositorySystem = locator.getService( RepositorySystem.class );


        DefaultRepositorySystemSession session = MavenRepositorySystemUtils.newSession();

        LocalRepository localRepo = new LocalRepository( localMavenRepository );
        session.setLocalRepositoryManager(repositorySystem.newLocalRepositoryManager(session, localRepo));

        //repositorySystemSession.setTransferListener( new ConsoleTransferListener() );
        //repositorySystemSession.setRepositoryListener( new ConsoleRepositoryListener() );

        // uncomment to generate dirty trees
        // repositorySystemSession.setDependencyGraphTransformer( null );

        this.repositorySystemSession = session;


    }

    private List<RemoteRepository> repositories()
    {

        ArrayList<RemoteRepository> remoteRepositories = new ArrayList<>();
        for (URI mavenRepository : this.mavenRepositories) {
            remoteRepositories.add(repository(mavenRepository.getHost(), mavenRepository.toString()));
        }
        remoteRepositories.add(repository("central", "http://central.maven.org/maven2/"));
        return remoteRepositories;
    }

    private static RemoteRepository repository(String id, String url)
    {
        return new RemoteRepository.Builder(id, "default", url)
                .setSnapshotPolicy(new RepositoryPolicy(true, RepositoryPolicy.UPDATE_POLICY_ALWAYS, RepositoryPolicy.CHECKSUM_POLICY_FAIL))
                .build();
    }


    public PluginInfo resolvePlugin(Plugin plugin) {

        PluginInfo pluginInfo = new PluginInfo();
        pluginInfo.setGroupId(plugin.getGroupId());
        pluginInfo.setArtifactId(plugin.getArtifactId());
        pluginInfo.setVersion(plugin.getVersion());
        pluginInfo.setConfig(new Properties());

        Artifact pluginArtifact = resolvePluginArtifact(plugin);

        pluginInfo.setFile(pluginArtifact.getFile());

        try {
            ArtifactDescriptorResult descriptorResult = repositorySystem.readArtifactDescriptor(repositorySystemSession, new ArtifactDescriptorRequest(pluginArtifact, repositories(), null));

            CollectRequest collectRequest = new CollectRequest();

            for (RemoteRepository repo : repositories()) {
                collectRequest.addRepository(repo);
            }
            for (Dependency dependency : descriptorResult.getDependencies()) {
                collectRequest.addDependency(dependency);
            }

            collectRequest.setManagedDependencies(descriptorResult.getManagedDependencies());

            DependencyRequest dependencyRequest = new DependencyRequest(collectRequest, DependencyFilterUtils.classpathFilter(JavaScopes.RUNTIME));

            DependencyResult dependencyResult = repositorySystem.resolveDependencies(repositorySystemSession, dependencyRequest);

            if(!dependencyResult.getCollectExceptions().isEmpty()) {
                throw new RuntimeException("Failed resolving plugin dependencies", dependencyResult.getCollectExceptions().get(0));
            }

            pluginInfo.getClassPath(JavaScopes.RUNTIME).add(new org.kantega.reststop.classloaderutils.Artifact(pluginArtifact.getGroupId(),
                    pluginArtifact.getArtifactId(),
                    pluginArtifact.getVersion(),
                    pluginArtifact.getFile()));

            for(ArtifactResult result : dependencyResult.getArtifactResults()) {
                Artifact a = result.getArtifact();
                pluginInfo.getClassPath(JavaScopes.RUNTIME).add(new org.kantega.reststop.classloaderutils.Artifact(a.getGroupId(), a.getArtifactId(), a.getVersion(), a.getFile()));
            }
            return pluginInfo;
        } catch (ArtifactDescriptorException | DependencyResolutionException e) {
            throw new RuntimeException(e);
        }
    }

    private Artifact resolvePluginArtifact(Plugin plugin) {
        Artifact artifact;
        try
        {
            artifact = new DefaultArtifact(plugin.getGroupId(), plugin.getArtifactId(), "jar", plugin.getVersion());
        }
        catch ( IllegalArgumentException e )
        {
            throw new RuntimeException( e.getMessage(), e );
        }

        ArtifactRequest request = new ArtifactRequest();
        request.setArtifact( artifact );
        request.setRepositories( repositories() );

        ArtifactResult result;
        try
        {
            result = repositorySystem.resolveArtifact(repositorySystemSession, request );
        }
        catch ( ArtifactResolutionException e )
        {
            throw new RuntimeException( e.getMessage(), e );
        }

        return result.getArtifact();
    }
}
