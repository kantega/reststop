package org.kantega.reststop.helloworld.jaxws;

import javax.jws.WebMethod;
import javax.jws.WebParam;
import javax.jws.WebResult;
import javax.jws.WebService;

/**
 *
 */
@WebService(serviceName = "HelloService",
        name = "Hello",
        targetNamespace = "http://reststop.kantega.org/ws/hello-1.0",
        wsdlLocation = "META-INF/wsdl/HelloService.wsdl")

public class HelloService {


    @WebMethod(operationName = "greet")
    @WebResult(name = "messageResult")
    public String sayHello(@WebParam(name = "receiver") String receiver, @WebParam(name = "lang") String lang) {
        return ("se".equals(lang) ? "Hej" : "Hello")  +", " + receiver +"!";
    }
}
