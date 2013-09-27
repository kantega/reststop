package org.kantega.reststop.helloworld;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;

/**
 *
 */
@WebService(serviceName = "HelloService", name = "Hello", targetNamespace = "http://reststop.kantega.org/ws/hello-1.0")
public class HelloService {

    @WebMethod(operationName = "greet")
    @WebResult(name = "messageResult")

    public String sayHello(@WebParam(name = "receiver") String melding, @WebParam(name = "lang") String lang) {
        return "Hei: " + melding;
    }
}
