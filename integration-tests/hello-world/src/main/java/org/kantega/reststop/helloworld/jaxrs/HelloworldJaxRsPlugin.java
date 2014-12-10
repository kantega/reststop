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

package org.kantega.reststop.helloworld.jaxrs;

import org.kantega.reststop.api.Config;
import org.kantega.reststop.jaxrsapi.DefaultJaxRsPlugin;

/**
 *
 */
public class HelloworldJaxRsPlugin extends DefaultJaxRsPlugin {


    @Config(defaultValue = "-1")
    private byte minusOneByte;

    @Config(defaultValue = "4")
    private short fourShort;

    @Config(defaultValue = "5")
    private short fiveInt;

    @Config(defaultValue = "6")
    private long sixLong;

    @Config(defaultValue = "1.0")
    private float oneFloat;

    @Config(defaultValue = "1.1")
    private double onePointOneDouble;

    @Config(defaultValue = "true")
    private boolean booleanTrue;


    @Config(defaultValue = "a")
    private char aChar;


    public HelloworldJaxRsPlugin() {
        addJaxRsSingletonResource(new HelloWorldRootResource());
        addJaxRsSingletonResource(new HelloworldResource());

        addJaxRsContainerClass(ValidationMessageFeature.class);
    }

}
