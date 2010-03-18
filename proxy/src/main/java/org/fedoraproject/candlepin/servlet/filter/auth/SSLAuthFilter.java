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
import java.security.cert.X509Certificate;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.log4j.Logger;

/**
 * SSLAuthFilter
 */
public class SSLAuthFilter implements Filter {
    private static final String CERTIFICATES_ATTR = "javax.servlet.request.X509Certificate";
    private static final String CURRENT_USERNAME = "username";
    
    private static Logger log = Logger.getLogger(SSLAuthFilter.class);
    // private FilterConfig filterConfig = null;

    public void init(FilterConfig filterConfig) throws ServletException {
        // this.filterConfig = filterConfig;
    }

    public void destroy() {
        // this.filterConfig = null;
    }

    @SuppressWarnings("serial")
    public void doFilter(ServletRequest request, ServletResponse response,
        FilterChain chain) throws IOException, ServletException {

        debugMessage("in ssl auth filter");
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        X509Certificate[] certs = (X509Certificate[]) httpRequest
            .getAttribute(CERTIFICATES_ATTR);

        if (null != request.getAttribute(CURRENT_USERNAME)) {
            debugMessage("leaving ssl auth filter: user has been already authenticated");
            chain.doFilter(request, response);
            return;
        }
            
        if (certs == null || certs.length < 1) {
            debugMessage("no certificate was present to authenticate the client");
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        // certs is an array of certificates presented by the client
        // with the first one in the array being the certificate of the client itself.
        request.setAttribute(CURRENT_USERNAME, certs[0].getSubjectDN().getName());
        
        chain.doFilter(request, response);
        debugMessage("leaving ssl auth filter");
    }

    private void debugMessage(String msg) {
        if (log.isDebugEnabled()) {
            log.debug(msg);
        }
    }
}
