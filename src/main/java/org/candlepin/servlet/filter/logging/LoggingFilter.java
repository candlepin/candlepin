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
package org.candlepin.servlet.filter.logging;

import org.apache.log4j.Logger;
import org.apache.log4j.MDC;

import java.io.IOException;
import java.util.Enumeration;
import java.util.UUID;

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

        long startTime = System.currentTimeMillis();
        HttpServletRequest castRequest = (HttpServletRequest) request;
        HttpServletResponse castResponse = (HttpServletResponse) response;

        // Generate a UUID for this request and store in log4j's thread local MDC.
        // Will be logged with every request if the ConversionPattern uses it.
        MDC.put("requestType", "req");
        String requestUUID = UUID.randomUUID().toString();
        MDC.put("requestUuid", requestUUID);

        // Add requestUuid to the serverRequest as an attribute, so tomcat can
        //   log it to the access log with "%{requestUuid}r"
        castRequest.setAttribute("requestUuid", requestUUID);

        // Report the requestUuid to the client in the response.
        // Not sure this is useful yet.
        castResponse.setHeader("x-candlepin-request-uuid", requestUUID);

        // Log some basic info about the request at INFO level:
        logBasicRequestInfo(castRequest);

        if (log.isDebugEnabled()) {
            LoggingRequestWrapper lRequest = new LoggingRequestWrapper(castRequest);
            LoggingResponseWrapper lResponse = new LoggingResponseWrapper(castResponse);
            logRequest(lRequest);
            chain.doFilter(lRequest, lResponse);
            logBasicResponseInfo(lResponse, startTime);
            logResponseBody(lResponse);
            lResponse.getWriter().close();
        }
        else {
            StatusResponseWrapper responseWrapper = new StatusResponseWrapper(castResponse);
            chain.doFilter(request, responseWrapper);
            logBasicResponseInfo(responseWrapper, startTime);
        }

    }

    private void logBasicRequestInfo(HttpServletRequest castRequest) {
        StringBuilder requestBuilder = new StringBuilder()
            .append("Request: verb=")
            .append(castRequest.getMethod()).append(", uri=")
            .append(castRequest.getRequestURI());
        if (castRequest.getQueryString() != null) {
            requestBuilder.append("?").append(castRequest.getQueryString());
        }
        log.info(requestBuilder.toString());
    }

    private void logBasicResponseInfo(StatusResponseWrapper responseWrapper,
        long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        log.info(
            new StringBuilder().append("Response: status=")
                .append(responseWrapper.getStatus())
                .append(", content-type=\"").append(responseWrapper.getContentType())
                .append("\", time=").append(duration).append("ms").toString());
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
    private void logResponseBody(LoggingResponseWrapper lResponse) {
        logBody("Response", lResponse);
    }

    /**
     * @param lRequest
     * @param headerNames
     */
    private void logHeaders(LoggingRequestWrapper lRequest) {
        Enumeration<?> headerNames = lRequest.getHeaderNames();
        StringBuilder builder = new StringBuilder();

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
