/*
 * Copyright 2015 Kantega AS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.kantega.reststop.security;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.xml.bind.DatatypeConverter;
import java.io.IOException;

/**
 *
 */
public class BasicAuthFilter implements Filter {
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) servletRequest;
        HttpServletResponse resp = (HttpServletResponse) servletResponse;

        String auth = req.getHeader("Authorization");

        if(auth != null) {

            final String[] usernameAndPassword = new String(DatatypeConverter.parseBase64Binary(auth.substring("Basic ".length())), "utf-8").split(":");
            if(usernameAndPassword[0].equals(usernameAndPassword[1])) {
                filterChain.doFilter(new HttpServletRequestWrapper(req) {
                    @Override
                    public String getRemoteUser() {
                        return usernameAndPassword[0];
                    }

                    @Override
                    public boolean isUserInRole(String role) {
                        return "manager".equals(role);
                    }
                }, servletResponse);

                return;
            }
        }


        resp.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        resp.setHeader("WWW-Authenticate", "Basic realm=\"protected\"");

    }

    @Override
    public void destroy() {

    }
}
