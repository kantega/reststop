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

import javax.annotation.security.RolesAllowed;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import javax.ws.rs.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 *
 */
@Path("helloworld/{lang}")
public class HelloworldResource {

    @GET
    @Produces({"application/json", "application/xml", })
    @RolesAllowed("manager")
    public Hello hello(@NotNull @PathParam(value = "lang") String lang,
                       @NotNull @Size(min = 2) @QueryParam("yo") String greet) {

        return getHello(lang);
    }

    @GET
    @Produces({"application/json", "application/xml", })
    @RolesAllowed("manager")
    @Path("async")
    public CompletionStage<Hello> asyncHello(@NotNull @PathParam(value = "lang") String lang,
                                             @NotNull @Size(min = 2) @QueryParam("yo") String greet){
            return CompletableFuture.supplyAsync(() -> getHello(lang));
    }

    private Hello getHello(String lang) {
        String message = "Hello world";

        switch(lang) {
            case "no": message = "Hei verden";break;
            case "se": message = "Hej värden";break;
            case "fr": message = "Bonjour tout le monde";break;
        }


        return new Hello(message);
    }
}
