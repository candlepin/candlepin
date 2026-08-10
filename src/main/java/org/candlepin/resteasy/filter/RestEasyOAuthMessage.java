/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.resteasy.filter;

import net.oauth.OAuth;
import net.oauth.OAuthMessage;

import org.jboss.resteasy.spi.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jakarta.ws.rs.core.MediaType;


/**
 * Creates a valid OAuth message off of the fake
 * HttpHeader which RestEasy provides
 */
public class RestEasyOAuthMessage extends OAuthMessage {

    private static Logger log = LoggerFactory.getLogger(RestEasyOAuthMessage.class);

    public RestEasyOAuthMessage(HttpRequest request) {
        super(request.getHttpMethod(),
            request.getUri().getRequestUri().toString(), getParameters(request));
        copyHeaders(request, getHeaders());
    }

    private static void copyHeaders(HttpRequest request,
        Collection<Map.Entry<String, String>> into) {
        Iterator<String> names =
            request.getHttpHeaders().getRequestHeaders().keySet().iterator();
        if (names != null) {
            while (names.hasNext()) {
                String name = names.next();
                Iterator<String> values = request.getHttpHeaders()
                    .getRequestHeader(name).iterator();
                while (values.hasNext()) {
                    into.add(new OAuth.Parameter(name, values.next()));
                }
            }
        }
    }

    /**
     * Retrieves a list of OAuth specific parameters from the provided HTTP request. This method returns all
     * of the OAuth parameters in the 'Authorization' header. This method also returns all of the form
     * parameters if the request has a Content-Type that is compatible with
     * 'application/x-www-form-urlencoded'.
     *
     * @param request
     *  the request to retrieve the OAuth parameters from
     *
     * @return a list of all the OAuth specific parameters
     */
    public static List<OAuth.Parameter> getParameters(HttpRequest request) {
        List<OAuth.Parameter> parameters = new ArrayList<>();
        List<String> headers = request.getHttpHeaders().getRequestHeader("Authorization");
        if (headers != null) {
            for (String header : headers) {
                for (OAuth.Parameter parameter : OAuthMessage.decodeAuthorization(header)) {
                    if (!"realm".equalsIgnoreCase(parameter.getKey())) {
                        parameters.add(parameter);
                    }
                }
            }
        }

        if (request.getHttpMethod() == null) {
            log.debug("Not adding form parameters due to null HTTP method");
            return parameters;
        }

        MediaType requestMediaType = request.getHttpHeaders().getMediaType();
        if (requestMediaType == null) {
            log.debug("Not adding form parameters due to missing Content-Type");
            return parameters;
        }

        // HttpRequest.getFormParameters requires that the request content-type is compatible with
        // application/x-www-form-urlencoded or else we will run into an IllegalArgumentException.
        if (!requestMediaType.isCompatible(MediaType.APPLICATION_FORM_URLENCODED_TYPE)) {
            log.debug("Not adding form parameters due to incompatible Content-Type");
            return parameters;
        }

        // Add all of the form parameters
        for (Map.Entry<String, List<String>> entry : request.getFormParameters().entrySet()) {
            String name = entry.getKey();
            for (String value : entry.getValue()) {
                parameters.add(new OAuth.Parameter(name, value));
            }
        }

        return parameters;
    }
}
