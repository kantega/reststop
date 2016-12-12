/**
 * Created by eirbjo on 11/12/16.
 */
module org.kantega.reststop.plugins.jersey {
    requires java.annotations.common;
    requires org.kantega.reststop.api;
    requires org.kantega.reststop.servlet.api;
    requires org.kantega.reststop.plugins.jaxrs.api;
    requires javax.servlet.api;
    requires javax.ws.rs.api;
    requires jersey.common;
    requires jersey.server;
    requires jersey.container.servlet.core;
    requires jersey.media.json.jackson;
}