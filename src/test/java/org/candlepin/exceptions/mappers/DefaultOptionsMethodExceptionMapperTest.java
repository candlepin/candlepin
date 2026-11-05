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
package org.candlepin.exceptions.mappers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.jboss.resteasy.spi.DefaultOptionsMethodException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
/**
 * DefaultOptionsMethodExceptionMapperTest
 */
public class DefaultOptionsMethodExceptionMapperTest extends
    TestExceptionMapperBase {

    @Test
    public void testExceptionWithResponse() {
        Response original = Response.serverError().entity("private response").header("Allow", "GET").build();
        DefaultOptionsMethodException exception = new DefaultOptionsMethodException("private exception", original);
        Response response = injector.getInstance(DefaultOptionsMethodExceptionMapper.class).toResponse(exception);

        assertEquals(500, response.getStatus());
        assertEquals("GET", response.getHeaderString("Allow"));
        this.verifyMessage(response, "Internal Server Error");
    }

    @ParameterizedTest
    @CsvSource({
        "400,Bad Request",
        "409,Request failed with status code 409",
        "499,Request failed with status code 499",
        "599,Request failed with status code 599"
    })
    public void testPreservesErrorStatusAndMatchingMessage(int status, String message) {
        Response original = Response.status(status).entity("private response").build();
        DefaultOptionsMethodException exception = new DefaultOptionsMethodException("private exception", original);
        Response response = this.injector.getInstance(DefaultOptionsMethodExceptionMapper.class).toResponse(exception);

        assertEquals(status, response.getStatus());
        this.verifyMessage(response, message);
    }

    @ParameterizedTest
    @CsvSource({
        "Content-Length,1",
        "content-length,1",
        "Content-Encoding,gzip",
        "content-encoding,gzip",
        "Content-Language,fr",
        "Content-Location,/private",
        "Content-Range,bytes 0-0/42",
        "Content-Disposition,attachment",
        "ETag,private-tag",
        "Last-Modified,Wed 01 Jan 2025 00:00:00 GMT",
        "Content-MD5,old-digest",
        "Digest,old-digest",
        "Content-Digest,old-digest",
        "Repr-Digest,old-digest"
    })
    public void testRemovesHeadersForReplacedEntity(String header, String value) {
        Response original = Response.serverError()
            .entity("private response")
            .type(MediaType.TEXT_PLAIN_TYPE)
            .header(header, value)
            .header(HttpHeaders.ALLOW, "GET")
            .header(HttpHeaders.RETRY_AFTER, "60")
            .build();
        DefaultOptionsMethodException exception = new DefaultOptionsMethodException("private exception", original);
        Response response = this.injector.getInstance(DefaultOptionsMethodExceptionMapper.class).toResponse(exception);

        assertNull(response.getHeaderString(header));
        assertEquals(MediaType.APPLICATION_JSON_TYPE, response.getMediaType());
        assertEquals("GET", response.getHeaderString(HttpHeaders.ALLOW));
        assertEquals("60", response.getHeaderString(HttpHeaders.RETRY_AFTER));
        this.verifyMessage(response, "Internal Server Error");
    }

    @Test
    public void testVerifyResponse() {
        DefaultOptionsMethodException dome =
            new DefaultOptionsMethodException("oops", null);
        DefaultOptionsMethodExceptionMapper domem =
            injector.getInstance(DefaultOptionsMethodExceptionMapper.class);
        Response r = domem.toResponse(dome);
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), r.getStatus());
        verifyMessage(r, "Internal Server Error");
    }

    @Test
    public void testPreservesSuccessfulOptionsResponse() {
        Response original = Response.ok().allow("GET", "OPTIONS").build();
        DefaultOptionsMethodException exception = new DefaultOptionsMethodException("private", original);
        Response response = injector.getInstance(DefaultOptionsMethodExceptionMapper.class).toResponse(exception);

        assertEquals(original, response);
    }

    @Override
    public Class<?> getMapperClass() {
        return DefaultOptionsMethodExceptionMapper.class;
    }
}
