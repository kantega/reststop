/**
 * Created by eirbjo on 10/12/16.
 */
module org.kantega.reststop.apt {
    requires java.xml.bind;
    requires jdk.compiler;
    requires org.kantega.reststop.api;
    requires org.kantega.reststop.classloaderutils;

    provides javax.annotation.processing.Processor with org.kantega.reststop.apt.PluginClassProcessor;
    provides javax.annotation.processing.Processor with org.kantega.reststop.apt.ConfigParameterProcessor;
    provides javax.annotation.processing.Processor with org.kantega.reststop.apt.ExportFieldProcessor;

    exports org.kantega.reststop.apt;
}