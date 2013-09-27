package org.kantega.reststop.helloworld;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

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
import javax.xml.ws.WebServiceException;
import java.util.Map;

/**
 *
 */
public class HelloServiceIT {

    @Test
    public void shouldRespond() throws TransformerException {

        Dispatch<Source> helloPort = getDispatch();

        Source invoke = helloPort.invoke(new StreamSource(getClass().getResourceAsStream("helloRequest.xml")));

        TransformerFactory.newInstance().newTransformer().transform(invoke, new StreamResult(System.out));

    }

    @Test(expected = WebServiceException.class)
    public void shouldFailBecauseOfNullReceiver() throws TransformerException {

        Dispatch<Source> helloPort = getDispatch();

        Source invoke = helloPort.invoke(new StreamSource(getClass().getResourceAsStream("helloRequest-fail.xml")));

        TransformerFactory.newInstance().newTransformer().transform(invoke, new StreamResult(System.out));

    }

    private Dispatch<Source> getDispatch() {
        Service helloService = Service.create(getClass().getResource("/META-INF/wsdl/HelloService.wsdl"),
                new QName("http://reststop.kantega.org/ws/hello-1.0", "HelloService"));

        Dispatch<Source> helloPort = helloService.createDispatch(new QName("http://reststop.kantega.org/ws/hello-1.0", "HelloPort"),
                Source.class, Service.Mode.PAYLOAD);

        BindingProvider prov = (BindingProvider)helloPort;
        Map<String,Object> rc = prov.getRequestContext();
        rc.put(BindingProvider.USERNAME_PROPERTY, "joe");
        rc.put(BindingProvider.PASSWORD_PROPERTY, "joe");
        rc.put(BindingProvider.ENDPOINT_ADDRESS_PROPERTY, "http://localhost:" + System.getProperty("reststopPort", "8080") + "/ws/hello-1.0");
        return helloPort;
    }
}
