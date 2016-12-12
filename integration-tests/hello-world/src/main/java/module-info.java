/**
 * Created by eirbjo on 11/12/16.
 */
module org.kantega.reststop.test.helloworld {


    requires org.kantega.reststop.api;
    requires org.kantega.reststop.servlet.api;
    requires org.kantega.reststop.plugins.jaxws.api;
    requires org.kantega.reststop.plugins.jaxrs.api;

    requires java.xml.bind;
    requires java.xml.ws;
    requires java.annotations.common;

    requires javax.servlet.api;
    requires javax.ws.rs.api;

    requires wicket.core;
    requires wicket.util;

    requires spring.context;
    requires spring.beans;
    requires spring.core;
    requires spring.web;
    requires spring.webmvc;

    requires validation.api;

}