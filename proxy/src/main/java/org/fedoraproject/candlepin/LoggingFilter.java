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
package org.fedoraproject.candlepin;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

import org.apache.log4j.Logger;

/**
 * LoggingFilter
 */
public class LoggingFilter implements Filter {
    
    private static Logger log = Logger.getLogger(LoggingFilter.class);
    
    private FilterConfig filterConfig = null;
    
    public void init(FilterConfig filterConfig) throws ServletException {
        this.filterConfig = filterConfig;
    }
    
    public void destroy() {
        this.filterConfig = null;
    }

    public void doFilter(ServletRequest request, ServletResponse response,
                         FilterChain chain)
        throws IOException, ServletException {
        
        if (log.isDebugEnabled()) {
            LoggingRequestWrapper lRequest = new LoggingRequestWrapper(
                (HttpServletRequest) request);
            if (lRequest.getQueryString() != null) {
                log.debug(String.format("Request: '%s %s?%s'", lRequest.getMethod(), 
                    lRequest.getRequestURL(),
                    lRequest.getQueryString()));
            } 
            else {
                log.debug(String.format("Request: '%s %s'", lRequest
                    .getMethod(), lRequest.getRequestURL()));
            }
            log.debug("Request Body: " + lRequest.getBody());
            chain.doFilter(lRequest, response);
        }
        else {
            chain.doFilter(request, response);
        }

    }
}
