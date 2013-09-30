package org.kantega.reststop.helloworld.springmvc;

import org.kantega.reststop.api.DefaultReststopPlugin;
import org.kantega.reststop.api.Reststop;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.DispatcherServlet;

import javax.servlet.ServletException;
import java.util.Properties;

/**
 *
 */
public class SpringMvcPlugin extends DefaultReststopPlugin {
    public SpringMvcPlugin(Reststop reststop) throws ServletException {

        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();

        context.scan(getClass().getPackage().getName());

        DispatcherServlet servlet = new DispatcherServlet(context);

        Properties properties = new Properties();
        String filterPath = "/spring/*";
        servlet.init(reststop.createServletConfig("spring", properties));
        addServletFilter(reststop.createServletFilter(servlet, filterPath));
    }
}
