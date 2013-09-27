package org.kantega.reststop.cxflogging;

import org.apache.cxf.interceptor.LoggingInInterceptor;
import org.apache.cxf.jaxws22.EndpointImpl;
import org.apache.cxf.transport.servlet.CXFNonSpringServlet;
import org.kantega.reststop.cxf.api.DefaultCxfPluginPlugin;

import javax.xml.ws.Endpoint;

/**
 *
 */
public class CxfLoggingPlugin extends DefaultCxfPluginPlugin {


    public CxfLoggingPlugin() {
        System.out.println("Hello world " + CXFNonSpringServlet.class);
    }

    @Override
    public void customizeEndpoint(Endpoint endpoint) {
        EndpointImpl e = (EndpointImpl) endpoint;

        e.getServer().getEndpoint().getInInterceptors().add(new LoggingInInterceptor());
    }
}
