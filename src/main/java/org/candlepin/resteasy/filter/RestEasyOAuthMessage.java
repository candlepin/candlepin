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

import org.jboss.resteasy.core.ResteasyContext;
import org.jboss.resteasy.plugins.providers.FormUrlEncodedProvider;
import org.jboss.resteasy.plugins.server.servlet.HttpServletInputMessage;
import org.jboss.resteasy.spi.HttpRequest;
import org.jboss.resteasy.util.Encode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedHashMap;
import jakarta.ws.rs.core.MultivaluedMap;


/**
 * Creates a valid OAuth message off of the fake
 * HttpHeader which RestEasy provides
 */
public class RestEasyOAuthMessage extends OAuthMessage {

    private static Logger log = LoggerFactory.getLogger(RestEasyOAuthMessage.class);

    public RestEasyOAuthMessage(HttpRequest request) {
        // Query parameters are collected below. Leaving them in the URL would sign them twice.
        super(request.getHttpMethod(),
            request.getUri().getAbsolutePath().toString(), getParameters(request));
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
     * Retrieves the decoded signature parameters from the provided HTTP request, including query parameters
     * and OAuth parameters in the 'Authorization' header. This method also returns all of the form
     * parameters if the request has a Content-Type that is compatible with
     * 'application/x-www-form-urlencoded'.
     *
     * @param request
     *  the request to retrieve the OAuth parameters from
     *
     * @return a list of the decoded signature parameters
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

        for (Map.Entry<String, List<String>> entry : request.getUri().getQueryParameters().entrySet()) {
            for (String value : entry.getValue()) {
                parameters.add(new OAuth.Parameter(entry.getKey(), value));
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

        // OAuth applies percent encoding during signature normalization, so supply decoded values.
        MultivaluedMap<String, String> formParameters = request instanceof HttpServletInputMessage servletRequest ?
            getServletFormParameters(servletRequest) : request.getDecodedFormParameters();
        for (Map.Entry<String, List<String>> entry : formParameters.entrySet()) {
            String name = entry.getKey();
            for (String value : entry.getValue()) {
                parameters.add(new OAuth.Parameter(name, value));
            }
        }

        return parameters;
    }

    private static MultivaluedMap<String, String> getServletFormParameters(HttpServletInputMessage request) {
        // Parse the body independently of RESTEasy's form caches when the stream is still available.
        MultivaluedMap<String, String> encodedParameters;
        try {
            encodedParameters = FormUrlEncodedProvider.parseForm(request.getInputStream(),
                request.getHttpHeaders().getMediaType().getParameters().get(MediaType.CHARSET_PARAMETER));
        }
        catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        MultivaluedMap<String, String> formParameters = Encode.decode(encodedParameters);
        if (formParameters.isEmpty()) {
            formParameters = getCachedServletFormParameters(request);
            if (formParameters.isEmpty()) {
                // A previous direct body read may have consumed the stream and populated this cache.
                formParameters = Encode.decode(request.getPutFormParameters());
            }
        }

        // Refresh both caches so downstream readers also retain all body values, even if RESTEasy
        // previously removed every value equal to a query parameter from its decoded form cache.
        MultivaluedMap<String, String> encodedCache = request.getPutFormParameters();
        encodedCache.clear();
        encodedCache.putAll(Encode.encode(formParameters));
        MultivaluedMap<String, String> decodedCache = request.getPutDecodedFormParameters();
        decodedCache.clear();
        decodedCache.putAll(formParameters);
        return formParameters;
    }

    private static MultivaluedMap<String, String> getCachedServletFormParameters(HttpServletInputMessage request) {
        MultivaluedMap<String, String> formParameters = new MultivaluedHashMap<>();
        HttpServletRequest servletRequest = ResteasyContext.getContextData(HttpServletRequest.class);
        if (servletRequest != null) {
            // Servlet parsing may have consumed the stream already. Recover the body from its original
            // combined map: remove one occurrence per query value, preserving identical body values.
            for (Map.Entry<String, String[]> entry : servletRequest.getParameterMap().entrySet()) {
                List<String> values = new ArrayList<>(List.of(entry.getValue()));
                List<String> queryValues = request.getUri().getQueryParameters().get(entry.getKey());
                if (queryValues != null) {
                    for (String queryValue : queryValues) {
                        values.remove(queryValue);
                    }
                }
                if (!values.isEmpty()) {
                    formParameters.put(entry.getKey(), values);
                }
            }
        }

        return formParameters;
    }
}
