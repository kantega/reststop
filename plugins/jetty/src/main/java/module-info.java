module org.kantega.reststop.plugins.jetty {

    requires org.kantega.reststop.api;
    requires org.kantega.reststop.servlet.api;
    requires org.kantega.reststop.servlet;

    requires java.annotations.common;
    requires javax.servlet.api;
    requires jetty.server;
    requires jetty.util;
    requires jetty.servlet;
}