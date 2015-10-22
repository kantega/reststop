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

package org.kantega.reststop.webjars;

import org.kantega.reststop.api.*;

import java.io.IOException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.*;

/**
 * Adds WebJarsFilter with webjars resources, and exports a map of all &lt;artifactId&gt;:&lt;version&gt;, for resource in filter,
 * making it easy to reference webjars in html files.
 */
public class WebjarsReststopPlugin extends DefaultReststopPlugin {

    @Export
    private final WebjarsVersions v;

    private Map<String, String> versions;

    public WebjarsReststopPlugin(final Reststop reststop, final ReststopPluginManager reststopPluginManager) {

        addServletFilter(reststop.createFilter(new WebJarsFilter(reststopPluginManager), "/webjars/*", FilterPhase.USER));

        v = new WebjarsVersions() {
            @Override
            public Map<String, String> getVersions() {
                return getVersionsForWebJars(reststopPluginManager);
            }
        };
    }

    private synchronized Map<String, String> getVersionsForWebJars(ReststopPluginManager reststopPluginManager) {

        if (versions == null) {
            versions = new HashMap<>();

            Set<String> webjars = new HashSet<>();

            try {
                for (ClassLoader loader : reststopPluginManager.getPluginClassLoaders()) {
                    Enumeration<URL> resources = loader.getResources("META-INF/resources/webjars/");
                    while (resources.hasMoreElements()) {
                        URL webJar = resources.nextElement();
                        String file = URLDecoder.decode(webJar.getFile(), "UTF-8");
                        file = file.substring(0, file.indexOf("!"));
                        webjars.add(file);

                    }
                }

            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            for (String webjar : webjars) {
                String file = webjar.substring(0, webjar.lastIndexOf("/"));

                String version = file.substring(file.lastIndexOf("/") + 1);
                String artifact = file.substring(0, file.lastIndexOf("/"));
                String artifactId = artifact.substring(artifact.lastIndexOf("/") + 1);

                String key = "versions_" + artifactId;
                if (versions.get(key) != null) {
                    VersionComparator cmp = new VersionComparator();
                    if (cmp.compare(version, versions.get(key)) == -1) {
                        versions.put(key, version);
                    }
                } else {
                    versions.put(key, version);
                }
            }

        }
        return getWithCorrectPath(versions);
    }

    private Map<String, String> getWithCorrectPath(Map<String, String> versions) {
        HashMap<String, String> v = new HashMap<>();
        for (String key : versions.keySet()) {
            String value = versions.get(key);
            int i = value.indexOf("-");
            if (i == -1) {
                v.put(key, value);
            } else {
                String number = value.substring(i);
                try {
                    int buildNumber = new Integer(number);
                    v.put(key, value.substring(0, i));
                } catch (NumberFormatException e) {
                    v.put(key, value);
                }
            }
        }
        return v;
    }
}

class VersionComparator implements Comparator {

    public boolean equals(Object o1, Object o2) {
        return compare(o1, o2) == 0;
    }

    public int compare(Object o1, Object o2) {
        String version1 = (String) o1;
        String version2 = (String) o2;

        VersionTokenizer tokenizer1 = new VersionTokenizer(version1);
        VersionTokenizer tokenizer2 = new VersionTokenizer(version2);

        int number1 = 0, number2 = 0;
        String suffix1 = "", suffix2 = "";

        while (tokenizer1.MoveNext()) {
            if (!tokenizer2.MoveNext()) {
                do {
                    number1 = tokenizer1.getNumber();
                    suffix1 = tokenizer1.getSuffix();
                    if (number1 != 0 || suffix1.length() != 0) {
                        // Version one is longer than number two, and non-zero
                        return 1;
                    }
                }
                while (tokenizer1.MoveNext());

                // Version one is longer than version two, but zero
                return 0;
            }

            number1 = tokenizer1.getNumber();
            suffix1 = tokenizer1.getSuffix();
            number2 = tokenizer2.getNumber();
            suffix2 = tokenizer2.getSuffix();

            if (number1 < number2) {
                // Number one is less than number two
                return -1;
            }
            if (number1 > number2) {
                // Number one is greater than number two
                return 1;
            }

            boolean empty1 = suffix1.length() == 0;
            boolean empty2 = suffix2.length() == 0;

            if (empty1 && empty2) continue; // No suffixes
            if (empty1) return 1; // First suffix is empty (1.2 > 1.2b)
            if (empty2) return -1; // Second suffix is empty (1.2a < 1.2)

            // Lexical comparison of suffixes
            int result = suffix1.compareTo(suffix2);
            if (result != 0) return result;

        }
        if (tokenizer2.MoveNext()) {
            do {
                number2 = tokenizer2.getNumber();
                suffix2 = tokenizer2.getSuffix();
                if (number2 != 0 || suffix2.length() != 0) {
                    // Version one is longer than version two, and non-zero
                    return -1;
                }
            }
            while (tokenizer2.MoveNext());

            // Version two is longer than version one, but zero
            return 0;
        }
        return 0;
    }
}

class VersionTokenizer {
    private final String _versionString;
    private final int _length;

    private int _position;
    private int _number;
    private String _suffix;
    private boolean _hasValue;

    public int getNumber() {
        return _number;
    }

    public String getSuffix() {
        return _suffix;
    }

    public boolean hasValue() {
        return _hasValue;
    }

    public VersionTokenizer(String versionString) {
        if (versionString == null)
            throw new IllegalArgumentException("versionString is null");

        _versionString = versionString;
        _length = versionString.length();
    }

    public boolean MoveNext() {
        _number = 0;
        _suffix = "";
        _hasValue = false;

        // No more characters
        if (_position >= _length)
            return false;

        _hasValue = true;

        while (_position < _length) {
            char c = _versionString.charAt(_position);
            if (c < '0' || c > '9') break;
            _number = _number * 10 + (c - '0');
            _position++;
        }

        int suffixStart = _position;

        while (_position < _length) {
            char c = _versionString.charAt(_position);
            if (c == '.') break;
            _position++;
        }

        _suffix = _versionString.substring(suffixStart, _position);

        if (_position < _length) _position++;

        return true;
    }
}