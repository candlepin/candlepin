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

import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;

/**
 * ServletLogger
 */
public class ServletLogger {

    protected ServletLogger() {

    }

    public static StringBuilder logHeaders(HttpServletRequest req) {
        Enumeration<?> headerNames = req.getHeaderNames();
        StringBuilder builder = new StringBuilder();

        builder.append("\n====Headers====");
        while (headerNames.hasMoreElements()) {
            String headerName = (String) headerNames.nextElement();
            builder.append("\n  ").append(headerName).append(": ")
                .append(req.getHeader(headerName));
        }
        builder.append("\n====End Headers====\n");
        return builder;
    }

    public static StringBuilder logHeaders(TeeHttpServletResponse resp) {
        Map<String, List<String>> headers = resp.getHeaders();
        StringBuilder builder = new StringBuilder();
        builder.append("\n====Headers====");
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            builder.append("\n");
            builder.append(header.getKey()).append(": ").append(header.getValue());
        }

        builder.append("\n====End Headers====\n");
        return builder;
    }

    public static StringBuilder logRequest(TeeHttpServletRequest req) {
        return logHeaders(req).append(logBody("Request", req));
    }

    public static StringBuilder logResponse(TeeHttpServletResponse resp) {
        return logHeaders(resp).append(logBody("Response", resp));
    }

    public static StringBuilder logBody(String type, BodyLogger bodyLogger) {
        StringBuilder builder = new StringBuilder();
        builder.append("====").append(type).append(" Body====\n");
        builder.append(bodyLogger.getBody());
        return builder;
    }

    public static StringBuilder logBasicRequestInfo(HttpServletRequest req) {
        StringBuilder requestBuilder = new StringBuilder()
            .append("Request: verb=")
            .append(req.getMethod()).append(", uri=")
            .append(req.getRequestURI());
        if (req.getQueryString() != null) {
            requestBuilder.append("?").append(req.getQueryString());
        }
        return requestBuilder;
    }

    public static StringBuilder logBasicResponseInfo(TeeHttpServletResponse resp,
        long startTime) {
        long duration = System.currentTimeMillis() - startTime;
        return new StringBuilder().append("Response: status=")
                .append(resp.getStatus())
                .append(", content-type=\"").append(resp.getContentType())
                .append("\", time=").append(duration);
    }

    public static boolean showAsText(String contentType) {
        String[] textTypes = {
            MediaType.APPLICATION_JSON,
            MediaType.APPLICATION_ATOM_XML,
            MediaType.TEXT_PLAIN,
            MediaType.TEXT_XML,
            MediaType.TEXT_HTML,
            MediaType.APPLICATION_FORM_URLENCODED
        };
        return Arrays.asList(textTypes).contains(contentType);
    }
}
