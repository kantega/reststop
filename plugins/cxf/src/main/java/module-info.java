/**
 * Created by eirbjo on 11/12/16.
 */
module org.kantega.reststop.plugins.cxf {
    requires java.annotations.common;
    requires java.xml.ws;
    requires javax.servlet.api;
    requires cxf.core;
    requires cxf.rt.wsdl;
    requires cxf.rt.transports.http;
    requires wsdl4j;
    requires org.kantega.reststop.plugins.jaxws.api;
    requires org.kantega.reststop.api;
    requires org.kantega.reststop.servlet.api;

    exports org.kantega.reststop.cxf;
}