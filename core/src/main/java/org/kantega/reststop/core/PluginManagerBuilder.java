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

package org.kantega.reststop.core;

import org.kantega.jexmec.*;
import org.kantega.jexmec.manager.DefaultPluginManager;
import org.kantega.reststop.api.Export;
import org.kantega.reststop.api.PluginExport;
import org.kantega.reststop.api.PluginListener;
import org.kantega.reststop.api.ReststopPluginManager;
import org.kantega.reststop.classloaderutils.*;

import javax.annotation.PreDestroy;
import java.io.File;
import java.lang.reflect.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.*;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static org.kantega.jexmec.manager.DefaultPluginManager.buildFor;

/**
 *
 */
public class PluginManagerBuilder {

    private static Logger log = Logger.getLogger(PluginManagerBuilder.class.getName());
    private final List<Consumer<ReststopPluginManager>> beforeStarts = new ArrayList<>();

    private Map<ServiceKey, Object> staticServices = new HashMap<>();
    private ClassLoaderProvider[] classLoaderProviders;


    public static PluginManagerBuilder builder() {
        return new PluginManagerBuilder();
    }

    public <T> PluginManagerBuilder withService(ServiceKey<T> serviceKey, T service) {
        staticServices.put(serviceKey, service);
        return this;
    }

    public PluginManagerBuilder beforeStart(Consumer<ReststopPluginManager> listener) {
        beforeStarts.add(listener);
        return this;
    }


    public DefaultReststopPluginManager build() {
        DefaultReststopPluginManager reststopPluginManager = new DefaultReststopPluginManager();

        DefaultReststop reststop = new DefaultReststop();

        final PluginExportsServiceLocator exportsServiceLocator = new PluginExportsServiceLocator();
        DefaultPluginManager<Object> manager = buildFor(Object.class)
                .withClassLoaderProvider(reststop)
                .withClassLoaderProviders(classLoaderProviders)
                .withClassLoader(getClass().getClassLoader())
                .withPluginLoader(new ReststopPluginLoader(exportsServiceLocator))
                .withService(ServiceKey.by(Reststop.class), reststop)
                .withService(ServiceKey.by(ReststopPluginManager.class), reststopPluginManager)
                .withServiceLocator(new StaticServiceLocator(staticServices))
                .withServiceLocator(exportsServiceLocator)
                .withConcretePluginClassAllowed()
                .build();

        exportsServiceLocator.setPluginManager(manager);
        reststopPluginManager.setExportsServiceLocator(exportsServiceLocator);

        reststopPluginManager.setManager(manager);
        manager.addPluginManagerListener(new PluginLifecyleListener(reststopPluginManager));

        beforeStarts.forEach(l -> l.accept(reststopPluginManager));
        manager.start();
        return reststopPluginManager;
    }

    public PluginManagerBuilder withClassLoaderProviders(ClassLoaderProvider[] classLoaderProviders) {
        this.classLoaderProviders = classLoaderProviders;
        return this;
    }

    public static class DefaultReststopPluginManager implements ReststopPluginManager {
        private volatile DefaultPluginManager manager;

        private final IdentityHashMap<ClassLoader, ClassLoader> classLoaders = new IdentityHashMap<>();
        private PluginExportsServiceLocator exportsServiceLocator;

        @Override
        public Collection<Object> getPlugins() {
            assertStarted();
            return (Collection<Object>) manager.getPlugins();
        }

        @Override
        public <T> Collection<T> getPlugins(Class<T> pluginClass) {
            assertStarted();
            return manager.getPlugins(pluginClass);
        }

        @Override
        public ClassLoader getClassLoader(Object plugin) {
            assertStarted();
            return manager.getClassLoader(plugin);
        }

        @Override
        public <T> Collection<T> findExports(Class<T> type) {
            return findPluginExports(type).stream().map(PluginExport::getExport).collect(Collectors.toList());
        }

        @Override
        public <T> Collection<PluginExport<T>> findPluginExports(Class<T> type) {
            return exportsServiceLocator.getPluginExports(ServiceKey.by(type));
        }

        private void assertStarted() {
            if (manager == null) {
                throw new IllegalStateException("Illegal to call getPlugins before PluginManager is fully started. Please add a listener instead!");
            }
        }

        public void setManager(DefaultPluginManager<Object> manager) {
            this.manager = manager;
            manager.addPluginManagerListener(new PluginManagerListener<Object>() {


                @Override
                public void afterClassLoaderAdded(PluginManager<Object> pluginManager, ClassLoaderProvider classLoaderProvider, ClassLoader classLoader) {
                    synchronized (classLoaders) {
                        classLoaders.put(classLoader, classLoader);
                    }

                }

                @Override
                public void beforeClassLoaderRemoved(PluginManager<Object> pluginManager, ClassLoaderProvider classLoaderProvider, ClassLoader classLoader) {
                    synchronized (classLoaders) {
                        classLoaders.remove(classLoader);
                    }
                }

                @Override
                public void beforeClassLoaderAdded(PluginManager<Object> pluginManager, ClassLoaderProvider classLoaderProvider, ClassLoader classLoader) {
                }


            });
        }

        @Override
        public Collection<ClassLoader> getPluginClassLoaders() {
            ArrayList<ClassLoader> copy;
            synchronized (classLoaders) {
                copy = new ArrayList<>(classLoaders.keySet());
            }
            return copy;
        }

        public void setExportsServiceLocator(PluginExportsServiceLocator exportsServiceLocator) {
            this.exportsServiceLocator = exportsServiceLocator;
        }

        public void start() {
            manager.start();
        }

        public void stop() {
            manager.stop();
        }
    }


    public static class PluginExportsServiceLocator implements ServiceLocator {
        private final Map<ClassLoader, Map<ServiceKey, List<PluginExport>>> servicesByClassLoader = new IdentityHashMap<>();


        private void setPluginManager(PluginManager<Object> pluginManager) {
            pluginManager.addPluginManagerListener(new PluginManagerListener<Object>() {
                @Override
                public void beforeActivation(PluginManager<Object> pluginManager, ClassLoaderProvider classLoaderProvider, ClassLoader classLoader, PluginLoader<Object> pluginLoader, Collection<Object> plugins) {
                    synchronized (servicesByClassLoader) {
                        for (Object plugin : plugins) {

                            for (Field field : plugin.getClass().getDeclaredFields()) {
                                if (field.getAnnotation(Export.class) != null) {
                                    try {
                                        field.setAccessible(true);
                                        Object service = field.get(plugin);
                                        if (service != null) {
                                            if (!servicesByClassLoader.containsKey(classLoader)) {
                                                servicesByClassLoader.put(classLoader, new HashMap<>());
                                            }
                                            Map<ServiceKey, List<PluginExport>> forClassLoader = servicesByClassLoader.get(classLoader);


                                            Class<?> type = field.getType();
                                            if (type == Collection.class) {
                                                ParameterizedType parameterizedType = (ParameterizedType) field.getGenericType();

                                                ServiceKey<?> serviceKey = ServiceKey.by((Class<?>) parameterizedType.getActualTypeArguments()[0]);

                                                if (!forClassLoader.containsKey(serviceKey)) {
                                                    forClassLoader.put(serviceKey, new ArrayList<>());
                                                }
                                                Collection<?> collection = (Collection<?>) service;
                                                collection.forEach((s) -> forClassLoader.get(serviceKey).add(new Exreg(classLoader, plugin, s)));
                                            } else {
                                                ServiceKey<?> serviceKey = ServiceKey.by(type);

                                                if (!forClassLoader.containsKey(serviceKey)) {
                                                    forClassLoader.put(serviceKey, new ArrayList<>());
                                                }
                                                forClassLoader.get(serviceKey).add(new Exreg(classLoader, plugin, service));
                                            }

                                        }
                                    } catch (IllegalAccessException e) {
                                        throw new RuntimeException(e);
                                    }

                                }
                            }


                        }
                    }
                }

                @Override
                public void beforeClassLoaderRemoved(PluginManager<Object> pluginManager, ClassLoaderProvider classLoaderProvider, ClassLoader classLoader) {
                    synchronized (servicesByClassLoader) {
                        servicesByClassLoader.remove(classLoader);
                    }
                }
            });

        }

        @Override
        public Set<ServiceKey> keySet() {
            synchronized (servicesByClassLoader) {
                Set<ServiceKey> keys = new HashSet<>();
                for (Map<ServiceKey, List<PluginExport>> forClassloader : servicesByClassLoader.values()) {
                    keys.addAll(forClassloader.keySet());
                }
                return keys;
            }

        }

        @Override
        public <T> T get(ServiceKey<T> serviceKey) {
            synchronized (servicesByClassLoader) {
                for (Map<ServiceKey, List<PluginExport>> forClassLoader : servicesByClassLoader.values()) {
                    List<PluginExport> impl = forClassLoader.get(serviceKey);
                    if (impl != null) {
                        return serviceKey.getType().cast(impl.get(0).getExport());
                    }
                }
                return null;

            }
        }


        public <T> Collection<T> getMultiple(ServiceKey<T> serviceKey) {
            synchronized (servicesByClassLoader) {
                List<T> collection = new ArrayList<>();
                for (Map<ServiceKey, List<PluginExport>> forClassLoader : servicesByClassLoader.values()) {
                    List<PluginExport> impl = forClassLoader.get(serviceKey);
                    if (impl != null) {
                        impl.forEach((er) -> collection.add(((T) er.getExport())));
                    }
                }
                return collection;

            }
        }

        public <T> Collection<PluginExport<T>> getPluginExports(ServiceKey<T> serviceKey) {
            synchronized (servicesByClassLoader) {
                Collection<PluginExport<T>> collection = new ArrayList<>();


                for (Map<ServiceKey, List<PluginExport>> forClassLoader : servicesByClassLoader.values()) {
                    List<PluginExport> impl = forClassLoader.get(serviceKey);
                    if (impl != null) {
                        impl.forEach((pe) -> collection.add(pe));
                    }
                }
                return collection;
            }
        }
    }

    private static class DefaultReststop implements Reststop, ClassLoaderProvider {
        private Registry registry;
        private ClassLoader parentClassLoader;

        public DefaultReststop() {

        }

        @Override
        public void start(Registry registry, ClassLoader parentClassLoader) {

            this.registry = registry;
            this.parentClassLoader = parentClassLoader;
        }

        @Override
        public void stop() {

        }

        @Override
        public ClassLoader getPluginParentClassLoader() {
            return parentClassLoader;
        }

        @Override
        public PluginClassLoaderChange changePluginClassLoaders() {
            return new DefaultClassLoaderChange(registry);
        }


        private static class DefaultClassLoaderChange implements PluginClassLoaderChange {
            private final Registry registry;
            private final List<ClassLoader> adds = new ArrayList<>();
            private final List<ClassLoader> removes = new ArrayList<>();

            public DefaultClassLoaderChange(Registry registry) {
                this.registry = registry;
            }

            @Override
            public PluginClassLoaderChange add(ClassLoader classLoader) {
                adds.add(classLoader);
                return this;
            }

            @Override
            public PluginClassLoaderChange remove(ClassLoader classLoader) {
                removes.add(classLoader);
                return this;
            }

            @Override
            public void commit() {
                log.info("About to commit class loader change:");
                log.info(" Removing : " + removes);
                for (ClassLoader add : adds) {
                    log.info("Adding " + add);
                    if (add instanceof URLClassLoader) {
                        URLClassLoader ucl = (URLClassLoader) add;
                        for (URL url : ucl.getURLs()) {
                            log.info("\t url: " + url.toString());
                        }
                    }
                }
                registry.replace(removes, adds);
            }
        }


    }

    private static class Exreg<T> implements PluginExport<T> {
        private final ClassLoader classLoader;
        private final Object plugin;
        private final T export;

        public Exreg(ClassLoader classLoader, Object plugin, T export) {
            this.classLoader = classLoader;
            this.plugin = plugin;
            this.export = export;
        }

        public Object getPlugin() {
            return plugin;
        }

        public T getExport() {
            return export;
        }

        @Override
        public ClassLoader getClassLoader() {
            return classLoader;
        }
    }

    private static class PluginLifecyleListener extends PluginManagerListener<Object> {

        private boolean pluginManagerStarted;
        private final ReststopPluginManager manager;


        private PluginLifecyleListener(ReststopPluginManager manager) {
            this.manager = manager;
        }

        @Override
        public void afterPluginManagerStarted(PluginManager pluginManager) {
            pluginManagerStarted = true;


            for (PluginExport<PluginListener> listenerExport : manager.findPluginExports(PluginListener.class)) {

                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                Thread.currentThread().setContextClassLoader(listenerExport.getClassLoader());
                try {
                    listenerExport.getExport().pluginManagerStarted();
                } finally {
                    Thread.currentThread().setContextClassLoader(loader);
                }
            }
        }

        @Override
        public void pluginsUpdated(Collection<Object> plugins) {
            if (pluginManagerStarted) {
                for (PluginExport<PluginListener> listenerExport : manager.findPluginExports(PluginListener.class)) {

                    ClassLoader loader = Thread.currentThread().getContextClassLoader();
                    Thread.currentThread().setContextClassLoader(listenerExport.getClassLoader());
                    try {
                        listenerExport.getExport().pluginsUpdated(plugins);
                    } finally {
                        Thread.currentThread().setContextClassLoader(loader);
                    }

                }
            }
        }

        @Override
        public void beforePassivation(PluginManager<Object> pluginManager, ClassLoaderProvider classLoaderProvider, ClassLoader classLoader, PluginLoader<Object> pluginLoader, Collection<Object> plugins) {
            List<Object> reversed = new ArrayList<>(plugins);
            Collections.reverse(reversed);
            for (Object plugin : reversed) {
                for (Method method : plugin.getClass().getDeclaredMethods()) {
                    if (method.isAnnotationPresent(PreDestroy.class)) {
                        if ((method.getModifiers() & Modifier.PUBLIC) == 1 && method.getReturnType() == Void.TYPE && method.getParameters().length == 0) {
                            try {
                                method.invoke(plugin);
                            } catch (IllegalAccessException | InvocationTargetException e) {
                                throw new RuntimeException(e);
                            }
                        } else {
                            throw new IllegalArgumentException("@PreDestroy annotated method " + method + " must be public void and have zero parameters");
                        }

                    }

                }
            }
        }

        @Override
        public void beforePluginManagerStopped(PluginManager<Object> pluginManager) {

        }

        @Override
        public void afterActivation(PluginManager<Object> pluginManager, ClassLoaderProvider classLoaderProvider, ClassLoader classLoader, PluginLoader<Object> pluginLoader, Collection<Object> plugins) {
            for (Object plugin : plugins) {
                // plugin.init();
            }
        }
    }

    private static class StaticServiceLocator implements ServiceLocator {
        private final Map<ServiceKey, Object> staticServices;

        public StaticServiceLocator(Map<ServiceKey, Object> staticServices) {
            this.staticServices = staticServices;
        }

        @Override
        public <T> T get(ServiceKey<T> serviceKey) {
            return (T) staticServices.get(serviceKey);
        }

        @Override
        public Set<ServiceKey> keySet() {
            return staticServices.keySet();
        }
    }


    public static class PluginInfosClassLoaderProvider implements ClassLoaderProvider {
        private final List<PluginInfo> pluginInfos;
        private final File repoDir;

        public PluginInfosClassLoaderProvider(List<PluginInfo> pluginInfos, File repoDir) {
            this.pluginInfos = pluginInfos;
            this.repoDir = repoDir;
        }

        @Override
        public void start(Registry registry, ClassLoader parentClassLoader) {
            List<ClassLoader> loaders = new ArrayList<>();
            Map<String, PluginClassLoader> byDep = new HashMap<>();


            List<PluginInfo> pluginsInClassloaderOrder = PluginInfo.resolveClassloaderOrder(pluginInfos);

            List<PluginInfo> toStart = new ArrayList<>();

            for (PluginInfo info : pluginsInClassloaderOrder) {

                if (info.isDirectDeploy()) {
                    PluginClassLoader pluginClassloader = new PluginClassLoader(info, getParentClassLoader(info, parentClassLoader, byDep));

                    File pluginJar = getPluginFile(info);

                    try {
                        pluginClassloader.addURL(pluginJar.toURI().toURL());
                        info.setFile(pluginJar);

                        for (Artifact artifact : info.getClassPath("runtime")) {
                            pluginClassloader.addURL(getPluginFile(artifact).toURI().toURL());

                        }
                    } catch (MalformedURLException e) {
                        throw new RuntimeException(e);
                    }

                    toStart.add(info);
                    loaders.add(pluginClassloader);
                    byDep.put(info.getGroupIdAndArtifactId(), pluginClassloader);

                }
            }


            List<ClassLoader> classLoadersInStartupOrder = new ArrayList<>();
            List<PluginInfo> pluginsInStartupOrder = PluginInfo.resolveStartupOrder(toStart);

            for (PluginInfo info : pluginsInStartupOrder) {
                String key = info.getGroupIdAndArtifactId();
                classLoadersInStartupOrder.add(byDep.get(key));
            }
            registry.add(classLoadersInStartupOrder);
        }

        private File getPluginFile(Artifact artifact) {
            if (repoDir != null) {
                return new File(repoDir,
                        artifact.getGroupId().replace('.', '/') + "/"
                                + artifact.getArtifactId() + "/"
                                + artifact.getVersion() + "/"
                                + artifact.getArtifactId() + "-" + artifact.getVersion() + ".jar");

            } else {
                return artifact.getFile();
            }
        }

        private ClassLoader getParentClassLoader(PluginInfo pluginInfo, ClassLoader parentClassLoader, Map<String, PluginClassLoader> byDep) {
            Set<PluginClassLoader> delegates = new HashSet<>();

            for (Artifact dep : pluginInfo.getDependsOn()) {
                PluginClassLoader dependencyLoader = byDep.get(dep.getGroupIdAndArtifactId());
                if (dependencyLoader != null) {
                    delegates.add(dependencyLoader);
                }
            }
            if (delegates.isEmpty()) {
                return parentClassLoader;
            } else {
                return new ResourceHidingClassLoader(new DelegateClassLoader(parentClassLoader, delegates), Object.class) {
                    @Override
                    protected boolean isLocalResource(String name) {
                        return name.startsWith("META-INF/services/ReststopPlugin/");
                    }
                };
            }
        }

        @Override
        public void stop() {

        }
    }
}