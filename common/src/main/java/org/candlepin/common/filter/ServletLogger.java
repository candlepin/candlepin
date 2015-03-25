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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

/**
 * ServletLogger
 */
public class ServletLogger {

    private static ObjectMapper mapper;
    private static ObjectWriter writer;

    static {
        mapper = new ObjectMapper();
        writer = mapper.writerWithDefaultPrettyPrinter();
    }

    protected ServletLogger() {
        // Static methods only
    }

    public static StringBuilder logHeaders(HttpServletRequest req) {
        Enumeration<?> headerNames = req.getHeaderNames();
        StringBuilder builder = new StringBuilder();

        while (headerNames.hasMoreElements()) {
            String headerName = (String) headerNames.nextElement();
            builder.append(headerName).append(": ").append(req.getHeader(headerName)).append("\n");
        }
        builder.append("---\n");
        return builder;
    }

    public static StringBuilder logHeaders(TeeHttpServletResponse resp) {
        Map<String, List<String>> headers = resp.getHeaders();
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, List<String>> header : headers.entrySet()) {
            builder.append(header.getKey()).append(": ").append(header.getValue()).append("\n");
        }

        builder.append("---\n");
        return builder;
    }

    public static StringBuilder logRequest(TeeHttpServletRequest req) {
        StringBuilder builder = new StringBuilder();
        builder.append("Request: ")
            .append(req.getMethod()).append(" ").append(req.getRequestURI());
        if (req.getQueryString() != null) {
            builder.append("?").append(req.getQueryString());
        }

        return builder
            .append(logHeaders(req))
            .append(logBody("Request", req, true));
    }

    public static StringBuilder logResponse(TeeHttpServletResponse resp, long startTime) {
        // Impl note:
        // We're not formatting JSON output here as our JsonProvider already takes care of that.
        long duration = System.currentTimeMillis() - startTime;

        StringBuilder builder = new StringBuilder();
        int statusCode = resp.getStatus();
        return builder.append("Response: ")
            .append(statusCode)
            .append(" ")
            .append(Response.Status.fromStatusCode(statusCode))
            .append(" (").append(duration).append(" ms)\n")
            .append(logHeaders(resp))
            .append(logBody("Response", resp, false));
    }

    public static StringBuilder logBody(String type, BodyLogger bodyLogger, boolean formatJson) {
        StringBuilder builder = new StringBuilder();
        String content = bodyLogger.getBody();

        if (formatJson && MediaType.APPLICATION_JSON.equals(bodyLogger.getContentType())) {
            // Ensure JSON is formatted for humans
            try {
                Object jobj = mapper.readValue(content, Object.class);
                content = writer.writeValueAsString(jobj);
            }
            catch (IOException e) {
                // This may happen if the JSON is malformed. We'll just leave the content alone in
                // such a case.
            }
        }

        builder.append(content);
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
