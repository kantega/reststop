package org.kantega.reststop.helloworld.springmvc;

import org.apache.wicket.protocol.http.WicketFilter;
import org.kantega.reststop.api.DefaultReststopPlugin;
import org.kantega.reststop.api.Reststop;
import org.springframework.web.servlet.DispatcherServlet;

import javax.servlet.ServletException;
import java.util.Properties;

/**
 *
 */
public class SpringMvcPlugin extends DefaultReststopPlugin {
    public SpringMvcPlugin(Reststop reststop) throws ServletException {

        ClassLoader loader = Thread.currentThread().getContextClassLoader();

        Thread.currentThread().setContextClassLoader(getClass().getClassLoader());
        try {
            DispatcherServlet servlet = new DispatcherServlet();
        servlet.setContextConfigLocation("classpath:org/kantega/reststop/helloworld/springmvc/spring.xml");
            Properties properties = new Properties();
            String filterPath = "/spring/*";
            properties.setProperty(WicketFilter.FILTER_MAPPING_PARAM, filterPath);
            servlet.init(reststop.createServletConfig("spring", properties));
            addServletFilter(reststop.createServletFilter(servlet, filterPath));
        } finally {
            Thread.currentThread().setContextClassLoader(loader);
        }

    }
}
