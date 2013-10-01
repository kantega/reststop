package org.kantega.reststop.helloworld.view;

import org.apache.wicket.util.io.IOUtils;
import org.kantega.reststop.api.DefaultReststopPlugin;
import org.kantega.reststop.api.Reststop;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 *
 */
public class IndexPagePlugin extends DefaultReststopPlugin {

    public IndexPagePlugin(Reststop reststop) {

        addServletFilter(reststop.createServletFilter(new IndexPage(), "/"));
    }

    private class IndexPage extends HttpServlet {

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {

            response.setContentType("text/html");

            IOUtils.copy(getClass().getResourceAsStream("index.html"), response.getOutputStream());
        }
    }
}

