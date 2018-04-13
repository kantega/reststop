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

package org.kantega.reststop.maven;

import org.kantega.reststop.classloaderutils.config.PluginConfigParam;
import org.kantega.reststop.classloaderutils.config.PluginConfigParams;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import java.io.IOException;
import java.io.InputStream;

/**
 *
 */
public class ParamsUnmarshaller {
    public PluginConfigParams unmarshal(InputStream is, DocumentBuilder documentBuilder) {
        PluginConfigParams params = new PluginConfigParams();
        try {
            Document doc = documentBuilder.parse(is);
            NodeList paramsList = doc.getDocumentElement().getElementsByTagName("param");

            for(int i = 0; i < paramsList.getLength(); i++) {
                PluginConfigParam param = new PluginConfigParam();
                params.add(param);
                Element paramE = (Element) paramsList.item(i);
                param.setParamName(paramE.getAttribute("name"));
                param.setType(paramE.getAttribute("type"));
                param.setDefaultValue(paramE.getAttribute("default-value"));
                param.setRequired(Boolean.parseBoolean(paramE.getAttribute("required")));
                NodeList docsList = paramE.getElementsByTagName("doc");
                if(docsList.getLength() > 0) {
                    param.setDoc(docsList.item(0).getTextContent());
                }
            }

            return params;
        } catch (SAXException | IOException e) {
            throw new RuntimeException(e);
        }
    }
}
