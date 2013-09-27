package org.kantega.reststop.helloworld;

import org.junit.Test;

import javax.xml.namespace.QName;
import javax.xml.transform.Source;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;
import javax.xml.ws.BindingProvider;
import javax.xml.ws.Dispatch;
import javax.xml.ws.Service;

/**
 *
 */
public class HelloServiceIT {

    @Test
    public void shouldRespond() throws TransformerException {

        Service helloService = Service.create(getClass().getResource("/META-INF/wsdl/HelloService.wsdl"),
                new QName("http://reststop.kantega.org/ws/hello-1.0", "HelloService"));

        Dispatch<Source> helloPort = helloService.createDispatch(new QName("http://reststop.kantega.org/ws/hello-1.0", "HelloPort"),
                Source.class, Service.Mode.PAYLOAD);

        BindingProvider prov = (BindingProvider)helloPort;
        prov.getRequestContext().put(BindingProvider.USERNAME_PROPERTY, "joe");
        prov.getRequestContext().put(BindingProvider.PASSWORD_PROPERTY, "joe");


        Source invoke = helloPort.invoke(new StreamSource(getClass().getResourceAsStream("helloRequest.xml")));

        TransformerFactory.newInstance().newTransformer().transform(invoke, new StreamResult(System.out));

    }
}
