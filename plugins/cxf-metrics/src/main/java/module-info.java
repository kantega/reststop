/**
 * Created by eirbjo on 11/12/16.
 */
module org.kantega.reststop.plugins.cxf.metrics {
    requires java.xml.ws;
    requires cxf.core;
    requires cxf.rt.frontend.jaxws;
    requires metrics.core;
    requires org.kantega.reststop.plugins.cxf;
    requires org.kantega.reststop.plugins.jaxws.api;
    requires org.kantega.reststop.api;
}