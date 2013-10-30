package org.kantega.reststop.hellojson;

import org.glassfish.jersey.server.mvc.Viewable;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

/**
 *
 */
@Path("jsp")
public class JspResource {

    @GET
    @Produces(MediaType.TEXT_HTML)
    public Viewable get() {
        return new Viewable("/template");
    }
}
