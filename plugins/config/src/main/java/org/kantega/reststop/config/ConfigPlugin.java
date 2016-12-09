package org.kantega.reststop.config;


import org.kantega.reststop.api.config.Config;
import org.kantega.reststop.api.Export;
import org.kantega.reststop.api.Plugin;
import org.kantega.reststop.api.ReststopPluginManager;
import org.kantega.reststop.classloaderutils.PluginClassLoader;
import org.kantega.reststop.classloaderutils.PluginInfo;
import org.kantega.reststop.core.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Parameter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

@Plugin
public class ConfigPlugin implements PluginMutator {

    private static Pattern sysPropPattern = Pattern.compile(".*\\$\\{(.*)\\}.*");

    @Export public final Injector<String> stringInjector = injector(String.class);

    @Export public final Injector<Byte> byteInjector = injector(byte.class, Byte.class);

    @Export public final Injector<Short> shortInjector = injector(short.class, Short.class);

    @Export public final Injector<Integer> intInjector = injector(int.class, Integer.class);

    @Export public final Injector<Long> longInjector = injector(long.class, Long.class);

    @Export public final Injector<Float> floatInjector = injector(float.class, Float.class);

    @Export public final Injector<Double> doubleInjector = injector(double.class, Double.class);

    @Export public final Injector<Boolean> booleanInjector = injector(boolean.class, Boolean.class);

    @Export public final Injector<Character> characterInjector = injector(char.class, Character.class);

    @Export public final Injector<Properties> propertiesInjector = injector(Properties.class);

    @Export final PluginMutator mutator = this;

    private Set<Injector> injectors = new HashSet(asList(stringInjector, byteInjector, shortInjector, intInjector, longInjector, floatInjector, doubleInjector, booleanInjector, characterInjector, propertiesInjector));

    private final DefaultReststopPluginManager pluginManager;

    private final File configFile;

    private volatile long lastConfigModified = -1;
    private volatile Properties config;


    public ConfigPlugin(ReststopPluginManager pluginManager) {

        this.pluginManager = (DefaultReststopPluginManager) pluginManager;
        configFile = this.pluginManager.getConfigFile();

        config = readConfig(configFile);
        configChanged();
    }


    private <T> Injector<T> injector(Class<T>... types) {
        return (parameter) -> {
            if (parameter.isAnnotationPresent(Config.class)) {
                for (Class type : types) {
                    if (parameter.getType() == type) {
                        return Optional.of((T) getParam(parameter));
                    }
                }
            }
            return Optional.empty();
        };
    }

    private Object getParam(Parameter parameter) {
        PluginClassLoader cl = (PluginClassLoader) parameter.getDeclaringExecutable().getDeclaringClass().getClassLoader();
        Properties props = readConfig(cl.getPluginInfo());
        Config config = parameter.getAnnotation(Config.class);

        return getConfigProperty(parameter, getParameterName(ReststopPluginLoader.beingLoaded.get(), parameter, config), props);
    }

    private String getParameterName(PluginClassInfo classInfo, Parameter parameter, Config config) {
        if(!"".equals(config.property())) {
            return config.property();
        }
        Parameter[] parameters = parameter.getDeclaringExecutable().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if(parameters[i] == parameter) {
                return classInfo.getParameterNames()[i];
            }
        }
        throw new IllegalStateException("Unknown parameter name for property " + parameter);
    }

    private Properties readConfig(PluginInfo pluginInfo) {

        File artifact = new File(configFile.getParentFile(), pluginInfo.getArtifactId() +".conf");
        File artifactVersion = new File(configFile.getParentFile(), pluginInfo.getArtifactId() +"-" + pluginInfo.getVersion() +".conf");

        Properties properties = new Properties();
        properties.putAll(pluginInfo.getConfig());

        addProperties(properties, configFile, artifact, artifactVersion);

        return properties;
    }

    private static void addProperties(Properties properties, File... files) {
        if(files != null) {
            for (File file : files) {
                if(file != null && file.exists()) {
                    Properties prop = new Properties();
                    try(FileInputStream in = new FileInputStream(file)) {
                        prop.load(in);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }

                    properties.putAll(prop);
                }
            }
        }
    }


    private Object getConfigProperty(Parameter param, String parameterName, Properties properties) {

        Config config = param.getAnnotation(Config.class);

        if(param.getType() == Properties.class) {
            Properties clone = new Properties();
            clone.putAll(properties);
            return clone;
        }
        String name = config.property();

        if(name.trim().isEmpty())  {
            name = parameterName;
        }
        String defaultValue = config.defaultValue().isEmpty() ? null : config.defaultValue();

        String value = properties.getProperty(name, defaultValue);

        if( (value == null || value.trim().isEmpty()) && config.required()) {
            throw new IllegalArgumentException("Configuration missing for required @Config parameter '" +name +"' in class " + param.getDeclaringExecutable().getDeclaringClass().getName());
        }

        if (value != null) {
            value = interpolate(properties.getProperty(name, defaultValue));
        }

        return convertValue(param, value, param.getType());


    }

    private static String interpolate(String value) {
        int start = 0;

        Set<String> props = new HashSet<>();

        Matcher matcher = sysPropPattern.matcher(value);
        while(matcher.find(start)) {
            String name = matcher.group(1);
            props.add(name);
            start = matcher.end();
        }
        for (String prop : props) {
            String property = System.getProperty(prop);
            if(property == null) {
                throw new IllegalArgumentException("Missing system property ${" + prop +"}");
            }
            value = value.replace("${" + prop +"}", property);
        }
        return value;
    }

    private static Object convertValue(Parameter parameter, String value, Class<?> type) {
        if(type == String.class) {
            return value;
        } else if(type == byte.class || type == Byte.class) {
            return Byte.parseByte(value);
        } else if(type == short.class || type == Short.class) {
            return Short.parseShort(value);
        } else if(type == int.class || type == Integer.class) {
            return Integer.parseInt(value);
        } else if(type == long.class || type == Long.class) {
            return Long.parseLong(value);
        } else if(type == float.class || type == Float.class) {
            return Float.parseFloat(value);
        } else if(type == double.class || type == Double.class) {
            return Double.parseDouble(value);
        } else if(type == boolean.class || type == Boolean.class) {
            return Boolean.parseBoolean(value);
        } else if(type == char.class || type == Character.class) {
            return value.charAt(0);
        }
        throw new IllegalArgumentException("Could not convert @Config for unknown type " + parameter.getType().getName() + " of parameter '" +parameter.getName() +"' in class " + parameter.getDeclaringExecutable().getDeclaringClass().getName());
    }

    public List<LoadedPluginClass> findConfiguredWith(Set<String> changedProps, PluginState pluginState) {
        return pluginState.getLoadedPlugins().stream()
                .filter(p -> isConfiguredWith(changedProps, p))
                .collect(Collectors.toList());
    }

    private boolean isConfiguredWith(Set<String> changedProps, LoadedPluginClass p) {

        for (Parameter parameter : p.getInjectors().keySet()) {

            if(! injectors.contains(p.getInjectors().get(parameter))) {
                continue;
            }

            if(parameter.isAnnotationPresent(Config.class)) {
                if(parameter.getType() == Properties.class) {
                    return true;
                }
                Config config = parameter.getAnnotation(Config.class);

                String parameterName = getParameterName(p.getPluginClassInfo(), parameter, config);

                if(changedProps.contains(parameterName)) {
                    return true;
                }
            }
        }
        return false;
    }

    private Set<String> getModifiedConfigProps() {
        if(configChanged()) {
            Properties newConfig = readConfig(pluginManager.getConfigFile());
            Set<String> changed = findChangedProperties(this.config, newConfig);
            this.config = newConfig;
            return changed;
        }
        return Collections.emptySet();
    }

    private Set<String> findChangedProperties(Properties config, Properties newConfig) {
        Set<String> oldNames = config.stringPropertyNames();
        Set<String> newNames = newConfig.stringPropertyNames();

        Set<String> allNames = new HashSet<>();
        allNames.addAll(oldNames);
        allNames.addAll(newNames);

        Set<String> changed = new HashSet<>();

        for (String name : allNames) {
            if(!oldNames.contains(name) || !newNames.contains(name) || !config.getProperty(name).equals(newConfig.getProperty(name))) {
                changed.add(name);
            }
        }
        return changed;
    }

    private Properties readConfig(File configFile) {
        try (InputStream is = new FileInputStream(configFile)) {
            Properties properties = new Properties();
            properties.load(is);
            return properties;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

    }

    private Set<Class> getTypesExportedBy(List<PluginClassInfo> newPlugins) {
        return newPlugins.stream().map(PluginClassInfo::getExports).flatMap(Set::stream).collect(Collectors.toSet());
    }

    @Override
    public boolean possibleUpdatePluginState() {
        Set<String> modifiedConfigProps = getModifiedConfigProps();
        if(modifiedConfigProps.isEmpty()) {
            return false;
        }
        PluginState pluginState = pluginManager.getPluginState();
        List<LoadedPluginClass> configuredWith = findConfiguredWith(modifiedConfigProps, pluginState);

        Set<Class> exportedTypes = getTypesExportedBy(configuredWith.stream().map(LoadedPluginClass::getPluginClassInfo).collect(Collectors.toList()));
        List<LoadedPluginClass> consumers = pluginState.findConsumers(exportedTypes);

        List<LoadedPluginClass> restarts = Stream.concat(configuredWith.stream(), consumers.stream())
                .distinct()
                .collect(Collectors.toList());

        pluginManager.restart(restarts);

        return true;
    }


    private boolean configChanged() {
        File configFile = pluginManager.getConfigFile();

        if(lastConfigModified == -1) {
            lastConfigModified = configFile.lastModified();
            return false;
        }

        long currentLastModified = configFile.lastModified();
        if(currentLastModified > lastConfigModified) {
            lastConfigModified = currentLastModified;
            return true;
        }
        return false;
    }
}
