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

/**
 * Base class for authentication filters.  Subclasses should only need to implement
 * the {@link #getUserName(javax.servlet.http.HttpServletRequest,
 * javax.servlet.http.HttpServletResponse)} method for a typical use case.
 */
public abstract class AuthenticationFilter implements Filter {

    @Override
    public void init(FilterConfig fc) throws ServletException { }

    @Override
    public void destroy() { }

    /**
     * {@inheritDoc}
     *
     * Responsible for checking the <code>request</code> for an existing username
     * and calling in to <code>getUserName</code> if none exists.
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
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        if (request.getAttribute(FilterConstants.USERNAME_ATTR) == null) {
            try {
                String username = getUserName(httpRequest, httpResponse);

                if (username != null) {
                    // we have a valid username, set it in the request and
                    request.setAttribute(FilterConstants.USERNAME_ATTR, username);
                }
            }
            catch (IOException e) {
                throw e;
            }
            catch (ServletException e) {
                throw e;
            }
            catch (Exception e) {
                // problem with the user extraction
                httpResponse.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
                return;
            }
        }

        // username has been set by a previous auth filter or this one
        chain.doFilter(request, response);
    }

    /**
     * Extracts the user name.
     *
     * @param request
     * @param response
     * @return the user's name, or <code>null</code> if the name is not present
     * @throws Exception
     */
    protected abstract String getUserName(HttpServletRequest request,
            HttpServletResponse response) throws Exception;

}
