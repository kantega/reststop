/**
 * Created by eirbjo on 10/12/16.
 */
module org.kantega.reststop.core {
    requires org.kantega.reststop.classloaderutils;
    requires org.kantega.reststop.api;
    requires org.kantega.reststop.bootstrap;
    requires java.xml;
    requires java.annotations.common;

    exports org.kantega.reststop.core;
}