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

package org.kantega.reststop.helloworld.jaxrs;

import org.kantega.reststop.api.Config;
import org.kantega.reststop.api.Export;
import org.kantega.reststop.api.Plugin;
import org.kantega.reststop.jaxrsapi.ApplicationBuilder;

import javax.ws.rs.core.Application;
import java.util.Properties;

/**
 *
 */
@Plugin
public class HelloworldJaxRsPlugin  {


    @Export
    private final Application helloApp;

    public HelloworldJaxRsPlugin(
            @Config(property = "minus.one.byte") byte minusOneByte,
            @Config short fourShort,
            @Config(defaultValue = "5") short fiveInt,
            @Config long sixLong,
            @Config float oneFloat,
            @Config double onePointOneDouble,
            @Config boolean booleanTrue,
            @Config char aChar,
            @Config Character aCharacter,
            @Config String aString,
            @Config Properties config,
            ApplicationBuilder applicationBuilder) {

        helloApp = applicationBuilder.application()
                .singleton(new HelloWorldRootResource())
                .singleton(new HelloworldResource())
                .resource(ValidationMessageFeature.class)
                .build();
    }

}
