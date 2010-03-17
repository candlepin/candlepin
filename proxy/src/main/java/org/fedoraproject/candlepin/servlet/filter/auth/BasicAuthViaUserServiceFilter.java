/**
 * Copyright (c) 2009 Red Hat, Inc.
 *
 * This software is licensed to you under the GNU General Public License,
 * version 2 (GPLv2). There is NO WARRANTY for this software, express or
 * implied, including the implied warranties of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. You should have received a copy of GPLv2
 * along with this software; if not, see
 * http://www.gnu.org/licenses/old-licenses/gpl-2.0.txt.
 *
 * Red Hat trademarks are not licensed under GPLv2. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.fedoraproject.candlepin.servlet.filter.auth;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.resource.ForbiddenException;
import org.fedoraproject.candlepin.service.UserServiceAdapter;

import com.google.inject.Inject;

/**
 * BasicAuthViaUserServiceFilter
 */
public class BasicAuthViaUserServiceFilter implements Filter {

    private Logger log = Logger.getLogger(BasicAuthViaDbFilter.class);

    private UserServiceAdapter userServiceAdapter;
    
    @Inject
    public BasicAuthViaUserServiceFilter(Config config,
        UserServiceAdapter userServiceAdapter) {

        this.userServiceAdapter = userServiceAdapter;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}

    @Override
    public void destroy() {}

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
        FilterChain chain) throws IOException, ServletException {

        log.debug("in basic auth filter");
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        if (httpRequest.getMethod().equals("POST")) {
            processPost(request, response, chain, httpRequest, httpResponse);
        } else {
            // Anything that is not a POST is passed through
            chain.doFilter(request, response);
        }
        log.debug("leaving basic auth filter");
    }

    private void processPost(ServletRequest request, ServletResponse response,
        FilterChain chain, HttpServletRequest httpRequest,
        HttpServletResponse httpResponse) throws IOException, ServletException {
        String auth = httpRequest.getHeader("Authorization");
       
        if (auth != null && auth.toUpperCase().startsWith("BASIC ")) {

            String userpassEncoded = auth.substring(6);
            String[] userpass = new String(Base64.decodeBase64(userpassEncoded))
                    .split(":");

            if (doAuth(userpass[0], userpass[1])) {
                request.setAttribute("username", userpass[0]);
                chain.doFilter(request, response);
            } else{
                httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            }
        } else {
            chain.doFilter(request, response);
        }
    }
    
    private boolean doAuth(String username, String password) throws ForbiddenException {
        return userServiceAdapter.validateUser(username, password);
    }
}
