/**
 * Created by eirbjo on 11/12/16.
 */
module org.kantega.reststop.servlet {
    requires org.kantega.reststop.core;
    requires org.kantega.reststop.api;
    requires org.kantega.reststop.servlet.api;
    requires org.kantega.reststop.classloaderutils;

    requires javax.servlet.api;

    requires java.xml;

    exports org.kantega.reststop.servlets;
}