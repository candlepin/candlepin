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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.exceptions.CandlepinException;
import org.candlepin.exceptions.ConflictException;
import org.candlepin.exceptions.ExceptionMessage;
import org.candlepin.exceptions.IseException;
import org.candlepin.exceptions.ServiceUnavailableException;
import org.candlepin.logging.LoggingUtil;
import org.candlepin.version.VersionUtil;

import org.jboss.resteasy.spi.BadRequestException;
import org.jboss.resteasy.spi.DefaultOptionsMethodException;
import org.jboss.resteasy.util.HttpHeaderNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;


public class RuntimeExceptionMapperTest extends TestExceptionMapperBase {

    @BeforeEach
    public void setUp() throws IOException, URISyntaxException {
        PrintStream ps = new PrintStream(new File(this.getClass()
            .getClassLoader().getResource("version.properties").toURI()));
        ps.println("version=${version}");
        ps.println("release=${release}");
        ps.close();
    }

    @Test
    public void testToResponseBasicRuntimeException() {
        when(req.getHeader(HttpHeaderNames.ACCEPT)).thenReturn("application/json");

        Response r = rem.toResponse(new RuntimeException("test exception"));

        assertNotNull(r);
        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), r.getStatus());
        MultivaluedMap<String, Object> mm =  r.getMetadata();
        assertNotNull(mm);
        assertEquals(MediaType.APPLICATION_JSON_TYPE, mm.get("Content-Type").get(0));
        assertEquals("${version}-${release}",
            r.getMetadata().get(VersionUtil.VERSION_HEADER).get(0));
        verifyMessage(r, "An unexpected error occurred");
    }

    @Disabled
    @Test
    public void defaultOptionsException() {
        Response forex = mock(Response.class);
        when(req.getHeader(HttpHeaderNames.ACCEPT)).thenReturn("application/json");

        Response r = rem.toResponse(new DefaultOptionsMethodException("", forex));
        assertEquals(forex, r);
    }

    @Test
    public void testNullAcceptHeader() {
        when(req.getHeader(HttpHeaderNames.ACCEPT)).thenReturn(null);
        Response r = rem.toResponse(new RuntimeException("null accept header"));
        assertNotNull(r);
        MultivaluedMap<String, Object> mm = r.getMetadata();
        assertNotNull(mm);
        assertEquals(MediaType.APPLICATION_JSON_TYPE, mm.get("Content-Type").get(0));
        assertEquals("${version}-${release}",
            r.getMetadata().get(VersionUtil.VERSION_HEADER).get(0));
        verifyMessage(r, "An unexpected error occurred");
    }

    @Test
    public void candlepinException() {
        CandlepinException ce = mock(CandlepinException.class);
        when(ce.httpReturnCode()).thenReturn(Status.CONFLICT);
        ExceptionMessage em = new ExceptionMessage().setDisplayMessage("you screwed up");
        when(ce.message()).thenReturn(em);
        when(req.getHeader(HttpHeaderNames.ACCEPT)).thenReturn("application/json");

        assertEquals("you screwed up", em.toString());
        Response r = rem.toResponse(ce);
        assertNotNull(r);
        assertEquals(Status.CONFLICT.getStatusCode(), r.getStatus());
        verifyMessage(r, "you screwed up");
    }

    @Test
    public void candlepinExceptionWithChildNotCandleping() {
        CandlepinException ce = mock(CandlepinException.class);
        when(ce.httpReturnCode()).thenReturn(Status.CONFLICT);
        when(ce.message()).thenReturn(
            new ExceptionMessage().setDisplayMessage("you screwed up"));
        when(req.getHeader(HttpHeaderNames.ACCEPT)).thenReturn("application/json");
        when(ce.getCause()).thenReturn(new ConflictException("you screwed up"));
        Response r = rem.toResponse(ce);
        assertNotNull(r);
        assertEquals(Status.CONFLICT.getStatusCode(), r.getStatus());
        verifyMessage(r, "you screwed up");
    }

    @Test
    public void testChildException() {
        when(req.getHeader(HttpHeaderNames.ACCEPT)).thenReturn("application/json");

        RuntimeException child = new RuntimeException("child ex");
        RuntimeException re = new RuntimeException("foobar", child);
        Response r = rem.toResponse(re);
        assertNotNull(r);
        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), r.getStatus());
        verifyMessage(r, "An unexpected error occurred");
    }

    @Test
    public void testJbossBadRequestException() {
        String foo = "foo";
        BadRequestException bre = new BadRequestException(foo);
        Response r = rem.toResponse(bre);

        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), r.getStatus());
        verifyMessage(r, "An unexpected error occurred");
    }

    @ParameterizedTest
    @CsvSource({
        "INTERNAL_SERVER_ERROR, false",
        "INTERNAL_SERVER_ERROR, true",
        "BAD_GATEWAY, false",
        "SERVICE_UNAVAILABLE, true",
        "GATEWAY_TIMEOUT, false"
    })
    public void testHidesServerErrorDetails(Status status, boolean wrapped) {
        String message = "https://internal.example/private secret-token";
        CandlepinException exception = new CandlepinException(status, message, new IOException("private cause"));
        when(this.req.getHeader(HttpHeaderNames.ACCEPT)).thenReturn("application/atom+xml");

        Response response = this.rem.toResponse(wrapped ? new RuntimeException("private wrapper", exception) :
            exception);

        assertEquals(status.getStatusCode(), response.getStatus());
        assertEquals(MediaType.APPLICATION_ATOM_XML_TYPE, response.getMediaType());
        this.verifyMessage(response, "An unexpected error occurred");
        assertEquals(message, exception.message().getDisplayMessage());
        assertThat(response.getHeaders().get(VersionUtil.VERSION_HEADER)).hasSize(1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"https://internal.example/private secret-token"})
    public void testHidesIseDetails(String message) {
        Response response = this.rem.toResponse(new IseException(message, new IOException("private cause")));

        assertEquals(500, response.getStatus());
        this.verifyMessage(response, "An unexpected error occurred");
    }

    @Test
    public void testPreservesServerErrorMetadata() {
        ServiceUnavailableException exception = new ServiceUnavailableException("private diagnostics")
            .setRetryAfterTime(60);
        try (MDC.MDCCloseable ignored = MDC.putCloseable(LoggingUtil.MDC_REQUEST_UUID_KEY, "test-request")) {
            Response response = this.rem.toResponse(exception);

            assertEquals(503, response.getStatus());
            assertEquals("60", response.getHeaderString("Retry-After"));
            assertEquals("test-request", ((ExceptionMessage) response.getEntity()).getRequestUuid());
            this.verifyMessage(response, "An unexpected error occurred");
        }
    }

    @ParameterizedTest
    @CsvSource({"BAD_REQUEST, false", "CONFLICT, true"})
    public void testPreservesClientErrorMessages(Status status, boolean wrapped) {
        CandlepinException exception = new CandlepinException(status, "Please correct the request");

        Response response = this.rem.toResponse(wrapped ? new RuntimeException(exception) : exception);

        assertEquals(status.getStatusCode(), response.getStatus());
        assertSame(exception.message(), response.getEntity());
        this.verifyMessage(response, "Please correct the request");
    }

    @Override
    public Class<?> getMapperClass() {
        return RuntimeExceptionMapper.class;
    }
}
