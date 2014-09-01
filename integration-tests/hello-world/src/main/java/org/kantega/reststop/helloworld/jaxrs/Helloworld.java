package org.kantega.reststop.helloworld.jaxrs;

import javax.xml.bind.annotation.XmlRootElement;
import java.net.URI;
import java.util.Map;
import java.util.TreeMap;

/**
 *
 */
@XmlRootElement
public class Helloworld {

    private Map<String, URI> languages = new TreeMap<>();

    public Helloworld addLanguage(String no, URI uri) {
        languages.put(no, uri);
        return this;
    }

    public Map<String, URI> getLanguages() {
        return languages;
    }
}
