/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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
package org.candlepin.spec.bootstrap.client.request;

import org.candlepin.invoker.client.ApiException;
import org.candlepin.invoker.client.Pair;
import org.candlepin.spec.bootstrap.client.ApiClient;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import okhttp3.Call;



/**
 * The Request class provides a fluent-style builder API for creating generic requests. The Request
 * instance needs an ApiClient instance to perform the actual work, and can be provided either
 * directly during construction, or can be pulled automatically from a client-level ApiClient
 * object.
 */
public class Request {

    private static final Pattern PATH_PARAM_REGEX = Pattern.compile("\\{([^}]+)\\}");

    private static final String CONTENT_TYPE_HEADER = "Content-Type";
    private static final String CONTENT_TYPE_JSON = "application/json";

    private final org.candlepin.invoker.client.ApiClient client;

    private String basePath;
    private String endpoint;
    private String method;
    private Object body;

    private Map<String, String> pathParams;
    private Map<String, String> headers;
    private Map<String, List<String>> queryParams;


    /**
     * Builds a new Request instance which will use the specified ApiClient to perform the actual
     * request upon execution.
     *
     * @param client
     *  the org.candlepin.ApiClient instance to use to execute the request
     *
     * @throws IllegalArgumentException
     *  if the provided client is null
     */
    public Request(org.candlepin.invoker.client.ApiClient client) {
        if (client == null) {
            throw new IllegalArgumentException("client is null");
        }

        this.client = client;

        this.pathParams = new HashMap<>();
        this.headers = new HashMap<>();
        this.queryParams = new HashMap<>();
    }

    /**
     * Builds a new Request instance which will use the org.candlepin.ApiClient backing the specified
     * client ApiClient to execute the request.
     *
     * @param client
     *  The client ApiClient instance to use to execute the request
     *
     * @throws IllegalArgumentException
     *  if the provided client is null
     *
     * @return
     *  a newly instantiated Request instance using the given ApiClient
     */
    public static Request from(ApiClient client) {
        if (client == null) {
            throw new IllegalArgumentException("client is null");
        }

        return new Request(client.getApiClient());
    }

    // TODO: add a .to method which accepts an endpoint enum which define the HTTP method and endpoint

    /**
     * Resets this Request instance, clearing any paths, body, headers, or parameters set on it.
     *
     * @return
     *  a reference to this Request
     */
    public Request reset() {
        this.basePath = null;
        this.endpoint = null;
        this.method = null;
        this.body = null;

        this.headers.clear();
        this.pathParams.clear();
        this.queryParams.clear();

        return this;
    }

    /**
     * Sets the HTTP method, or verb, to use for this request. If the method is not explicitly set,
     * it will default to "GET".
     *
     * @param method
     *  the HTTP method to use for this request, such as GET, PUT, DELETE, etc.
     *
     * @return
     *  a reference to this Request
     */
    public Request setMethod(String method) {   // TODO: change to an enum
        this.method = method;
        return this;
    }

    /**
     * Sets the base path to use for executing this request. The base path represents the domain,
     * port, and any common endpoint prefix; and is not modified by any path parameters, even in the
     * case of an exact match. For example: "localhost:8443/candlepin".
     * <p></p>
     * Setting the base path is optional, and if omitted, will default to the base path provided by
     * the backing ApiClient object.
     *
     * @param path
     *  the path to set as the base path
     *
     * @return
     *  a reference to this Request
     */
    public Request setBasePath(String path) {
        this.basePath = path;
        return this;
    }

    /**
     * Sets the endpoint path for this request. The endpoint path represents the path to the target
     * endpoint, minus the common components of the path that would be captured by the base path.
     * <p></p>
     * The endpoint path may optionally include parameters which will be replaced by the values set
     * in any path parameters later. For example, in the path "/owners/{owner_key}/products", the
     * "{owner_key}" segment is a path parameter, and will be replaced by the value set for
     * "owner_key".
     *
     * @param path
     *  the endpoint path to set for this request
     *
     * @return
     *  a reference to this Request
     */
    public Request setPath(String path) {
        this.endpoint = path;
        return this;
    }

    /**
     * Sets a path parameter for this request. The value provided will take the place of all
     * instances of the key in the endpoint path at the time of execution. The key should not
     * include the curly-brace delimiters.
     *
     * @param key
     *  the name or key of the path parameter to set, sans curly-brace delimiters; cannot be null
     *  or empty
     *
     * @param value
     *  the value to set for the path parameter; cannot be null or empty
     *
     * @throws IllegalArgumentException
     *  if either the key or value are null or empty
     *
     * @return
     *  a reference to this Request
     */
    public Request setPathParam(String key, String value) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("key is null or empty");
        }

        if (value == null || value.isEmpty()) {
            throw new IllegalArgumentException("value is null or empty");
        }

        this.pathParams.put(key, value);
        return this;
    }

    /**
     * Adds a header to the request. Headers are not multi-value, and repeatedly setting a header
     * will cause the last value set to be used at the time of execution.
     *
     * @param header
     *  the name of the header to set; cannot be null or empty
     *
     * @param value
     *  the value of the header
     *
     * @throws IllegalArgumentException
     *  if the header is null or empty
     *
     * @return
     *  a reference to this Request
     */
    public Request addHeader(String header, String value) {
        if (header == null || header.isEmpty()) {
            throw new IllegalArgumentException("header is null or empty");
        }

        this.headers.put(header, value);
        return this;
    }

    /**
     * Adds a query parameter to the request. Query parameters are multi-value, and repeatedly
     * setting a given query parameter will cause all of its assigned values to be individually
     * mapped in the request. For example, if the query parameter "key" is assigned to values "a",
     * "b", and "c", the request URI will contain the string "key=a&key=b&key=c".
     *
     * @param key
     *  the query parameter name, or key, to set; cannot be null or empty
     *
     * @param value
     *  the value to assign to the query parameter
     *
     * @throws IllegalArgumentException
     *  if the key is null or empty
     *
     * @return
     *  a reference to this Request
     */
    public Request addQueryParam(String key, String value) {
        if (key == null || key.isEmpty()) {
            throw new IllegalArgumentException("key is null or empty");
        }

        this.queryParams.computeIfAbsent(key, (k) -> new ArrayList<>())
            .add(value);

        return this;
    }

    /**
     * Sets the body of the request. If the provided body is a non-string object, it will be
     * JSON-serialized, and the "Content-Type" header will be set to "application/json".
     *
     * @param body
     *  the body to set for this request
     *
     * @throws JsonSerializationException
     *  if the given body is a non-string object which cannot be serialized to JSON
     *
     * @return
     *  a reference to this Request
     */
    public Request setBody(Object body) {
        // Impl note: this will be serialized by the backing ApiClient
        this.body = body;
        return this;
    }

    /**
     * Executes this request synchronously, returning the response as a container object.
     * <p></p>
     * <strong>Note:</strong> This method will <strong>not</strong> throw an exception as a result
     * of a non-200 response code.
     *
     * @throws IllegalStateException
     *  if required request details, such as the endpoint path, have not yet been provided
     *
     * @return
     *  a Response object containing the HTTP response code, headers, and body
     */
    public Response execute() {
        if (this.endpoint == null) {
            throw new IllegalStateException("endpoint has not been set");
        }

        // Translate the bits we need to translate...
        String path = PATH_PARAM_REGEX.matcher(this.endpoint)
            .replaceAll((match) -> this.pathParams.get(match.group(1)));

        String method = this.method != null ? this.method : "GET";

        List<Pair> qparams = this.queryParams.entrySet()
            .stream()
            .flatMap((entry) -> entry.getValue().stream().map((value) -> new Pair(entry.getKey(), value)))
            .collect(Collectors.toList());

        // Ensure a content type has been set so we don't explode
        this.headers.computeIfAbsent(CONTENT_TYPE_HEADER, (key) -> CONTENT_TYPE_JSON);

        // TODO: Add more things here as necessary (forms? cookies?)

        try {
            Call call = this.client.buildCall(this.basePath, path, method, qparams, List.of(), this.body,
                this.headers, Map.of(), Map.of(), new String[] {  }, null);

            return new Response(call.execute());
        }
        catch (IOException | ApiException e) {
            throw new RequestException(e);
        }
    }

}
