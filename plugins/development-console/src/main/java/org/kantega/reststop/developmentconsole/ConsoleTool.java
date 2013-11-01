package org.kantega.reststop.developmentconsole;

import org.kantega.reststop.api.Export;
import org.kantega.reststop.api.ReststopPlugin;

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
}
