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

import org.candlepin.spec.bootstrap.client.ApiClient;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;



/**
 * An encapsulated response generated from a request. The response contains the HTTP response code,
 * response headers, and the body in binary format. This class also provides utility methods for
 * translating the body to various objects.
 */
public class Response {
    private final int code;
    private final byte[] body;
    private final Map<String, List<String>> headers;

    /**
     * Create a new Response from the given OkHttp Response object.
     *
     * @param response
     *  the OkHttp response object from which to build this response instance
     *
     * @throws IllegalArgumentException
     *  if the inbound response object is null
     *
     * @throws ResponseProcessingException
     *  if the response code, body, or headers cannot be read or processed
     */
    public Response(okhttp3.Response response) {
        if (response == null) {
            throw new IllegalArgumentException("response is null");
        }

        try (ResponseBody body = response.body()) {
            this.code = response.code();
            this.headers = response.headers().toMultimap();
            this.body = body != null ? body.bytes() : new byte[] { };
        }
        catch (IOException e) {
            throw new ResponseProcessingException(e);
        }
    }

    /**
     * Fetches the HTTP response code for this response.
     *
     * @return
     *  the HTTP response code for this response
     */
    public int getCode() {
        return this.code;
    }

    /**
     * Fetches the HTTP headers from the response as a multi-value map, mapping the header names as
     * keys to a list of values provided for that header.
     *
     * @return
     *  the HTTP headers for this response as a multi-value map
     */
    public Map<String, List<String>> getHeaders() {
        return this.headers;
    }

    /**
     * Fetches the body of the response as a binary blob.
     *
     * @return
     *  the body of the response as a binary blob
     */
    public byte[] getBody() {
        return this.body;

    }

    /**
     * Fetches the body of the response as a UTF-8 encoded string.
     *
     * @return
     *  the body of the response as a UTF-8 encoded string
     */
    public String getBodyAsString() {
        return this.getBodyAsString(StandardCharsets.UTF_8);
    }

    /**
     * Fetches the body of the response as a string encoded with the specified charset.
     *
     * @param charset
     *  the character encoding to use when converting the body to a string
     *
     * @throws IllegalArgumentException
     *  if charset is null
     *
     * @return
     *  the body of the response as a string encoded with the specified charset
     */
    public String getBodyAsString(Charset charset) {
        if (charset == null) {
            throw new IllegalArgumentException("charset is null");
        }

        return new String(this.body, charset);
    }

    /**
     * Attempts to process the body as a JSON-formatted UTF-8 string representing the given object
     * class. If the body cannot be successfully deserialized into an object of the given class,
     * this method throws an exception.
     *
     * @param type
     *  the type of object to attempt to deserialize the bod
     *
     * @throws JsonDeserializationException
     *  if the body cannot be deserialized into an object of the given class
     *
     * @return
     *  an instance of the specified class type, populated with the data contained in the body of
     *  this response
     */
    public <T> T deserialize(Class<T> type) {
        try {
            return ApiClient.MAPPER.readValue(this.getBody(), type);
        }
        catch (IOException e) { // Impl note: JsonProcessingException *is* an IOException
            throw new JsonDeserializationException(e);
        }
    }

    /**
     * Attempts to process the body as a JSON-formatted UTF-8 string representing the given object
     * class. If the body cannot be successfully deserialized into an object of the given class,
     * this method throws an exception.
     *
     * @param typeref
     *  A TypeReference indicating the object type the JSON data represents
     *
     * @throws JsonDeserializationException
     *  if the body cannot be deserialized into an object of the given class
     *
     * @return
     *  an instance of the specified class type, populated with the data contained in the body of
     *  this response
     */
    public <T> T deserialize(TypeReference<T> typeref) {
        try {
            return ApiClient.MAPPER.readValue(this.getBody(), typeref);
        }
        catch (IOException e) { // Impl note: JsonProcessingException *is* an IOException
            throw new JsonDeserializationException(e);
        }
    }

    /**
     * Attempts to process the body as a JSON-formatted UTF-8 string representing the given object
     * class. If the body cannot be successfully deserialized into an object of the JsonNode,
     * this method throws an exception.
     *
     * @throws JsonDeserializationException
     *  if the body cannot be deserialized into an object of the given class
     *
     * @return
     *  an instance of the JsonNode, populated with the data contained in the body of
     *  this response
     */
    public JsonNode deserialize() {
        try {
            return ApiClient.MAPPER.readTree(this.getBody());
        }
        catch (IOException e) { // Impl note: JsonProcessingException *is* an IOException
            throw new JsonDeserializationException(e);
        }
    }
}
