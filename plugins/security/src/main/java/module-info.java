/**
 * Created by eirbjo on 11/12/16.
 */
module org.kantega.reststop.plugins.security {
    requires java.xml.bind;
    requires java.annotations.common;
    requires jersey.server;
    requires javax.servlet.api;
    requires org.kantega.reststop.api;
    requires org.kantega.reststop.servlet.api;
    requires org.kantega.reststop.plugins.jaxrs.api;

}