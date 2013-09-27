package org.kantega.reststop.helloworld;

import javax.jws.WebService;

/**
 *
 */
@WebService
public class HelloService {

    public String sayHello() {
        return "Hei";
    }
}
