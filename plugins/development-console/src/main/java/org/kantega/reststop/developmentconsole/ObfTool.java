package org.kantega.reststop.developmentconsole;

/**
 *
 */
public class ObfTool {

    public String obf(String name, String value) {
        String s = name.toLowerCase();
        if(s.contains("password") || s.contains("pwd")) {
            return "****";
        } else {
            return value;
        }
    }
}
