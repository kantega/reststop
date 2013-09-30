package org.kantega.reststop.helloworld;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
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
