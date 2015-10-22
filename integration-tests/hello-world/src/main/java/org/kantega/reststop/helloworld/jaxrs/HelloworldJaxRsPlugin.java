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

/**
 *
 */
@Plugin
public class HelloworldJaxRsPlugin  {


    @Export
    private final Application helloApp;

    public HelloworldJaxRsPlugin(
            @Config(defaultValue = "-1") byte minusOneByte,
            @Config(defaultValue = "4") short fourShort,
            @Config(defaultValue = "5") short fiveInt,
            @Config(defaultValue = "6") long sixLong,
            @Config(defaultValue = "1.0") float oneFloat,
            @Config(defaultValue = "1.1") double onePointOneDouble,
            @Config(defaultValue = "true") boolean booleanTrue,
            @Config(defaultValue = "a") char aChar,
            @Config(defaultValue = "a") Character aCharacter,
            @Config(defaultValue = "a") String aString,
            ApplicationBuilder applicationBuilder) {

        helloApp = applicationBuilder.application()
                .singleton(new HelloWorldRootResource())
                .singleton(new HelloworldResource())
                .resource(ValidationMessageFeature.class)
                .build();
    }

}
