package org.kantega.reststop.helloworld.springmvc;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.io.Writer;

/**
 *
 */
@Controller
@RequestMapping( "hello")
public class HelloController {

    @RequestMapping(method = RequestMethod.GET)
    public void hello(Writer out) throws IOException {
        out.write("Hello World, it's Spring time!");
    }
}
