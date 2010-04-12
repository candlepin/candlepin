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
import javax.servlet.http.HttpServletResponse;

/**
 * This is assumed to be the final filter in the authentication chain, and is
 * responsible for verifying that a user has been authenticated by some previous
 * filter.
 */
public class AuthValidationFilter implements Filter {

    @Override
    public void init(FilterConfig fc) throws ServletException { }

    @Override
    public void destroy() { }

    /**
     * {@inheritDoc}
     *
     * This filter checks the request for a "username" attribute and throws
     * a 403 if none is found.
     *
     * @param request
     * @param response
     * @param chain
     * @throws IOException
     * @throws ServletException
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (request.getAttribute(FilterConstants.PRINCIPAL_ATTR) == null) {
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
        }
        else {
            chain.doFilter(request, response);
        }
    }


}
