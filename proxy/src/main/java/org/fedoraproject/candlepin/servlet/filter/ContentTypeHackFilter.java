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
package org.fedoraproject.candlepin.servlet.filter;

import java.io.IOException;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.apache.log4j.Logger;

import com.google.inject.Singleton;

/**
 * ContentTypeHackFilter
 *
 * RESTEasy 2.2.1GA requires GET requests with no body to still have a content type set,
 * which at least some of our client stuff (the ruby lib) doesn't do. If you don't have
 * the content type set, you'll end up getting an HTTP 415 response. This servlet filter
 * simply sets a content type if none is detected.
 */
@Singleton // For use with Guice
public class ContentTypeHackFilter implements Filter {
    private static Logger log = Logger.getLogger(ContentTypeHackFilter.class);

    @Override
    public void destroy() {
        // Nothing to do here
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
        FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        if (httpRequest.getContentType() == null) {
            httpRequest = new HttpServletRequestWrapper(httpRequest) {
                @Override
                public String getContentType() {
                    return "application/json";
                }
            };
        }
        chain.doFilter(httpRequest, response);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        // Nothing to do here
    }
}
