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

import org.kantega.reststop.helloworld.jaxrs.Helloworld;
import org.kantega.reststop.helloworld.jaxrs.HelloworldResource;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.UriBuilder;
import java.net.URI;

import static java.util.Arrays.asList;

/**
 *
 */
@Path("/helloworld")
public class HelloWorldRootResource {

    @GET
    @Produces({"application/json", "application/xml", })
    @RolesAllowed("manager")
    public Helloworld hello() {
        Helloworld languages = new Helloworld();
        for(String altLang : asList("no", "se", "en", "fr"))  {
            URI uri = UriBuilder.fromResource(HelloworldResource.class)
                    .build(altLang);
            languages.addLanguage(altLang, uri);
        }

        return languages;

    }
}
