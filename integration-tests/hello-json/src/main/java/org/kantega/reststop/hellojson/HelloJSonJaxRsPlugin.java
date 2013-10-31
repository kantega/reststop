package org.kantega.reststop.hellojson;

import org.glassfish.jersey.server.mvc.MvcProperties;
import org.glassfish.jersey.server.mvc.jsp.JspMvcFeature;
import org.glassfish.jersey.server.mvc.jsp.JspProperties;
import org.kantega.reststop.jaxrsapi.DefaultJaxRsPlugin;

import javax.ws.rs.Path;

/*
 * Copyright 2013 Kantega AS
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


public class HelloJSonJaxRsPlugin extends DefaultJaxRsPlugin {

    public HelloJSonJaxRsPlugin() {
        addJaxRsSingletonResource(new HelloJsonRootResource());
        addJaxRsContainerClass(JspMvcFeature.class);
        setProperty(JspProperties.TEMPLATES_BASE_PATH, "WEB-INF/jsp");

        addJaxRsSingletonResource(new JspResource());
    }
}
