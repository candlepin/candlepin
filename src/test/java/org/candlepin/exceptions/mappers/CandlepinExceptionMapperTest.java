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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.exceptions.ExceptionMessage;
import org.candlepin.guice.I18nProvider;
import org.candlepin.guice.TestingScope;
import org.candlepin.logging.LoggingUtil;
import org.candlepin.version.VersionUtil;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.servlet.RequestScoped;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.MDC;
import org.xnap.commons.i18n.I18n;

import java.io.IOException;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.ResponseBuilder;
import jakarta.ws.rs.core.Response.Status;



/**
 * CandlepinExceptionMapperTest
 */
public class CandlepinExceptionMapperTest {

    private Injector injector;
    private CandlepinExceptionMapper cem;

    @BeforeEach
    public void init() {
        MapperTestModule mtm = new MapperTestModule();
        injector = Guice.createInjector(mtm);
        cem = injector.getInstance(CandlepinExceptionMapper.class);
    }

    @Test
    public void testDefaultBuilder() {
        IOException ioe = new IOException("fake io exception");
        RuntimeException re = new RuntimeException("oops", ioe);
        ResponseBuilder bldr = cem.getDefaultBuilder(re, null,
            MediaType.APPLICATION_ATOM_XML_TYPE);
        Response r = bldr.build();
        ExceptionMessage em = (ExceptionMessage) r.getEntity();

        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), r.getStatus());
        assertEquals(MediaType.APPLICATION_ATOM_XML_TYPE,
            r.getMetadata().get("Content-Type").get(0));
        assertEquals("An unexpected error occurred", em.getDisplayMessage());
    }

    @Test
    public void testHandleArrayIndexException() {
        // Java 7 has a nice constructor where you can pass in false to not
        // fill in the stacktrace, but Java 6 does not have such a facility.
        // No amount of combination of creating Throwable without a stack
        // trace was working. We're resorting to using a mock version which
        // works great and is exactly the behavior we want.

        Throwable nostack = mock(Throwable.class);
        when(nostack.getMessage()).thenReturn("no stack trace");
        // simulate Java 7 on a Java 6 vm
        when(nostack.getStackTrace()).thenReturn(null);

        Response r = cem.getDefaultBuilder(nostack, null,
            MediaType.APPLICATION_JSON_TYPE).build();
        ExceptionMessage em = (ExceptionMessage) r.getEntity();

        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), r.getStatus());
        assertEquals("An unexpected error occurred", em.getDisplayMessage());
        assertEquals(MediaType.APPLICATION_JSON_TYPE,
            r.getMetadata().get("Content-Type").get(0));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"https://internal.example/private", "secret-token"})
    public void testHidesExceptionDetails(String message) {
        RuntimeException exception = new RuntimeException(message, new IOException("private cause"));
        exception.setStackTrace(new StackTraceElement[] {
            new StackTraceElement("private.Class", "secretMethod", "Private.java", 42)
        });

        Response response = this.cem.getDefaultBuilder(exception, Status.BAD_REQUEST).build();

        assertEquals(400, response.getStatus());
        assertEquals("An unexpected error occurred", ((ExceptionMessage) response.getEntity()).getDisplayMessage());
        assertThat(response.getHeaderString(VersionUtil.VERSION_HEADER)).isNotBlank();
    }

    @Test
    public void testPreservesRequestUuid() {
        try (MDC.MDCCloseable ignored = MDC.putCloseable(LoggingUtil.MDC_REQUEST_UUID_KEY, "test-request")) {
            Response response = this.cem.getDefaultBuilder(new RuntimeException("private"), null).build();

            assertEquals("test-request", ((ExceptionMessage) response.getEntity()).getRequestUuid());
        }
    }

    @ParameterizedTest
    @CsvSource({
        "BAD_REQUEST, Bad Request",
        "UNAUTHORIZED, Unauthorized",
        "FORBIDDEN, Forbidden",
        "NOT_FOUND, Not Found",
        "METHOD_NOT_ALLOWED, Method Not Allowed",
        "NOT_ACCEPTABLE, Not Acceptable",
        "UNSUPPORTED_MEDIA_TYPE, Unsupported Media Type",
        "INTERNAL_SERVER_ERROR, Internal Server Error",
        "CONFLICT, Request failed with status code 409"
    })
    public void testMapsStatusToMessage(Status status, String expected) {
        assertEquals(expected, this.cem.getStatusMessage(status));
    }

    @Test
    public void testDefaultsStatusMessageToInternalServerError() {
        assertEquals("Internal Server Error", this.cem.getStatusMessage(null));
    }

    public static class MapperTestModule extends AbstractModule {
        @Override
        protected void configure() {
            bindScope(RequestScoped.class, TestingScope.EAGER_SINGLETON);
            bind(CandlepinExceptionMapper.class);
            bind(HttpServletRequest.class).toInstance(mock(HttpServletRequest.class));
            bind(ServletRequest.class).toInstance(mock(HttpServletRequest.class));
            bind(I18n.class).toProvider(I18nProvider.class);
        }
    }
}
