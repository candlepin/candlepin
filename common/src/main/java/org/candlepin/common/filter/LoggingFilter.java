/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.common.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.UUID;

import javax.inject.Singleton;
import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * LoggingFilter
 *
 * This class must be a Singleton as described in
 * <a href="http://code.google.com/p/google-guice/wiki/ServletModule#Filter_Mapping">
 * the Guice documentation</a>.
 */
@Singleton
public class LoggingFilter implements Filter {

    private static Logger log = LoggerFactory.getLogger(LoggingFilter.class);

    private String headerName;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        headerName = filterConfig.getInitParameter("header.name");
    }

    @Override
    public void destroy() {
        headerName = null;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response,
        FilterChain chain) throws IOException, ServletException {

        long startTime = System.currentTimeMillis();
        TeeHttpServletRequest req = new TeeHttpServletRequest(
            (HttpServletRequest) request);
        TeeHttpServletResponse resp = new TeeHttpServletResponse(
            (HttpServletResponse) response);

        try {
            // Generate a UUID for this request and store in the thread local MDC.
            // Will be logged with every request if the ConversionPattern uses it.
            MDC.put("requestType", "req");
            String requestUUID = UUID.randomUUID().toString();
            MDC.put("requestUuid", requestUUID);

            // Add requestUuid to the serverRequest as an attribute, so Tomcat can
            // log it to the access log with "%{requestUuid}r"
            req.setAttribute("requestUuid", requestUUID);

            // Report the requestUuid to the client in the response.
            // Not sure this is useful yet.
            resp.setHeader(headerName, requestUUID);

            log.info("{}", ServletLogger.logBasicRequestInfo(req));
            if (log.isDebugEnabled()) {
                log.debug("{}", ServletLogger.logRequest(req));
            }

            chain.doFilter(req, resp);

            log.info("{}", ServletLogger.logBasicResponseInfo(resp, startTime));
            if (log.isDebugEnabled()) {
                log.debug("{}", ServletLogger.logResponse(resp));
            }

            resp.finish();
        }
        finally {
            MDC.clear();
        }
    }
}
