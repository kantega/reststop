package org.kantega.reststop.statistics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.DatatypeConverter;
import java.io.IOException;

/**

 */
public class StatisticsFilter implements Filter {

    private static final Logger LOG = LoggerFactory.getLogger("STATISTICS");
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        long before = System.currentTimeMillis();
        filterChain.doFilter(servletRequest,servletResponse);

        HttpServletRequest req = (HttpServletRequest) servletRequest;
        HttpServletResponse resp = (HttpServletResponse) servletResponse;

        StringBuilder b = new StringBuilder();
        b.append(req.getRequestURI());

        String remoteUser = req.getRemoteUser();

        if( remoteUser != null){
            b.append("[").append(remoteUser).append("]");
        }

        b.append(" ").append(resp.getStatus()).append(" ").append(String.valueOf(System.currentTimeMillis() - before));

        LOG.info( b.toString());

    }

    @Override
    public void destroy() {
    }
}
