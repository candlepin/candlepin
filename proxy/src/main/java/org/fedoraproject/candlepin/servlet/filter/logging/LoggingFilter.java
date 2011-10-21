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
package org.fedoraproject.candlepin.servlet.filter.logging;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.Enumeration;

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
 */
public class LoggingFilter implements Filter {

    private static Logger log = Logger.getLogger(LoggingFilter.class);

    public void init(FilterConfig filterConfig) throws ServletException {
        // Nothing to do here
    }

    public void destroy() {
        // Nothing to do here
    }

    public void doFilter(ServletRequest request, ServletResponse response,
        FilterChain chain) throws IOException, ServletException {

        if (log.isDebugEnabled()) {
            LoggingRequestWrapper lRequest = new LoggingRequestWrapper(
                (HttpServletRequest) request);
            LoggingResponseWrapper lResponse = new LoggingResponseWrapper(
                (HttpServletResponse) response);
            logRequest(lRequest);
            chain.doFilter(lRequest, lResponse);
            logResponse(lResponse);
            lResponse.getWriter().close();
        }
        else {
            chain.doFilter(request, response);
        }
    }

    /**
     * @param lRequest
     */
    private void logRequest(LoggingRequestWrapper lRequest) {
        logHeaders(lRequest);
        logBody("Request", lRequest);
    }

    /**
     * @param lResponse
     */
    private void logResponse(LoggingResponseWrapper lResponse) {
        log.debug(
            new StringBuilder().append("\n====Response====")
                .append("\n  Status: ").append(lResponse.getStatus())
                .append("\n  Content-type: ").append(lResponse.getContentType())
                .append("\n====Response====")
        );
        logBody("Response", lResponse);
    }

    /**
     * @param lRequest
     * @param headerNames
     */
    private void logHeaders(LoggingRequestWrapper lRequest) {
        Enumeration<?> headerNames = lRequest.getHeaderNames();
        StringBuilder builder =
            new StringBuilder().append("\nRequest: ")
                .append(lRequest.getMethod()).append("  ")
                .append(lRequest.getRequestURL());
        if (lRequest.getQueryString() != null) {
            builder.append("?").append(lRequest.getQueryString());
        }
        builder.append("\n====Headers====");
        while (headerNames.hasMoreElements()) {
            String headerName = (String) headerNames.nextElement();
            builder.append("\n  ").append(headerName).append(": ")
                .append(lRequest.getHeader(headerName));
        }
        builder.append("\n====Headers====");
        log.debug(builder);
    }

    private void logBody(String type, BodyLogger bodyLogger) {
        // Don't log file download responses, they make a mess of the log:
        if (log.isDebugEnabled() && (bodyLogger.getContentType() == null ||
            (!bodyLogger.getContentType().equals("application/x-download") &&
            !bodyLogger.getContentType().equals("application/zip")))) {
            log.debug("====" + type + "Body====");
            log.debug(bodyLogger.getBody());
        }
    }
}
