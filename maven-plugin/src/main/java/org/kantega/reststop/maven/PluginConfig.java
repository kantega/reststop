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

package org.kantega.reststop.maven;


import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlElement;
import java.util.ArrayList;
import java.util.List;

/**
 *
 */
public class PluginConfig {
    private String className;
    private List<PluginConfigParam> params = new ArrayList<>();


    public void setClassName(String className) {
        this.className = className;
    }

    public void addParam(PluginConfigParam pluginConfigParam) {
        params.add(pluginConfigParam);
    }

    @XmlAttribute(name = "class")
    public String getClassName() {
        return className;
    }

    @XmlElement(name = "param")
    public List<PluginConfigParam> getParams() {
        return params;
    }
}
