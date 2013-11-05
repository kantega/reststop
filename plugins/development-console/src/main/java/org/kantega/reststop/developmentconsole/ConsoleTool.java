package org.kantega.reststop.developmentconsole;

import org.kantega.reststop.api.Export;
import org.kantega.reststop.api.ReststopPlugin;
import org.kantega.reststop.classloaderutils.DelegateClassLoader;
import org.kantega.reststop.classloaderutils.PluginClassLoader;
import org.kantega.reststop.classloaderutils.PluginInfo;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public class ConsoleTool {

    public Map<Class, Object> getExports(ReststopPlugin plugin) throws IllegalAccessException {

        Map<Class, Object> exports = new HashMap<>();

        for (Field field : plugin.getClass().getDeclaredFields()) {
            if (field.getAnnotation(Export.class) != null) {
                field.setAccessible(true);
                exports.put(field.getType(), field.get(plugin));
            }
        }
        return exports;
    }

    public boolean isParentUsed(PluginClassLoader classLoader, PluginInfo parent) {
        if(classLoader.getParent().getParent() instanceof DelegateClassLoader) {
            DelegateClassLoader delegateClassLoader = (DelegateClassLoader) classLoader.getParent().getParent();

            return delegateClassLoader.isParentUsed(parent);
        } else {
            return false;
        }

    }
}
