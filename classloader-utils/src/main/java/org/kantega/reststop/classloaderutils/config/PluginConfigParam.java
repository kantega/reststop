/*
 * Copyright 2018 Kantega AS
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

package org.kantega.reststop.classloaderutils.config;


/**
 *
 */
public class PluginConfigParam {
    private String paramName;

    private String type;

    private String defaultValue;

    private String doc;

    private boolean required;

    public PluginConfigParam(String paramName, String type, String defaultValue, String doc, boolean required) {
        this.paramName = paramName;
        this.type = type;
        this.defaultValue = defaultValue;
        this.doc = doc;
        this.required = required;
    }

    public PluginConfigParam() {

    }

    public void setParamName(String paramName) {
        this.paramName = paramName;
    }

    public void setType(String type) {
        this.type = type;
    }


    public void setDefaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public String getParamName() {
        return paramName;
    }

    public String getDefaultValue() {
        return defaultValue;
    }

    public boolean isRequired() {
        return required;
    }

    public String getType() {
        return type;
    }

    public boolean hasDefaultValue() {
        return !"".equals(getDefaultValue());
    }

    public String getDoc() {
        return doc;
    }

    public void setDoc(String doc) {
        this.doc = doc;
    }

    public boolean hasDocumentation() {
        return getDoc() != null && !"".equals(getDoc());
    }

    public boolean isOptional() {
        return !isRequired() || hasDefaultValue();
    }
}
