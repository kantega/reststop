package org.kantega.reststop.helloworld.springmvc;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 *
 */
@Controller
@RequestMapping( "hello")
public class HelloController {

    @RequestMapping(method = RequestMethod.GET)
    public String hello(HttpServletResponse response) throws IOException {
        response.getWriter().write("Hello world");
        return null;
    }
}
