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
package org.canadianTenPin.exceptions.mappers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.canadianTenPin.exceptions.ExceptionMessage;
import org.canadianTenPin.guice.I18nProvider;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.junit.Before;
import org.junit.Test;
import org.xnap.commons.i18n.I18n;

import java.io.IOException;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;



/**
 * CanadianTenPinExceptionMapperTest
 */
public class CanadianTenPinExceptionMapperTest {

    private Injector injector;
    private CanadianTenPinExceptionMapper cem;


    @Before
    public void init() {
        MapperTestModule mtm = new MapperTestModule();
        injector = Guice.createInjector(mtm);
        cem = injector.getInstance(CanadianTenPinExceptionMapper.class);
    }

    @Test
    public void defaultBuilder() {
        IOException ioe = new IOException("fake io exception");
        RuntimeException re = new RuntimeException("oops", ioe);
        ResponseBuilder bldr = cem.getDefaultBuilder(re, null,
            MediaType.APPLICATION_ATOM_XML_TYPE);
        Response r = bldr.build();
        ExceptionMessage em = (ExceptionMessage) r.getEntity();

        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), r.getStatus());
        assertEquals(MediaType.APPLICATION_ATOM_XML_TYPE,
            r.getMetadata().get("Content-Type").get(0));
        assertTrue(em.getDisplayMessage().startsWith("Runtime Error " + re.getMessage()));
    }

    @Test
    public void handleArrayIndexException() {
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
        assertEquals("Runtime Error no stack trace", em.getDisplayMessage());
        assertEquals(MediaType.APPLICATION_JSON_TYPE,
            r.getMetadata().get("Content-Type").get(0));
    }

    public static class MapperTestModule extends AbstractModule {

        @Override
        protected void configure() {
            bind(CanadianTenPinExceptionMapper.class);
            bind(I18n.class).toProvider(I18nProvider.class);
            bind(HttpServletRequest.class).toInstance(mock(HttpServletRequest.class));
        }

    }
}
