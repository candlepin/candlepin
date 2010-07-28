/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.exceptions;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

import org.apache.log4j.Appender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.log4j.spi.LoggingEvent;
import org.fedoraproject.candlepin.guice.I18nProvider;
import org.jboss.resteasy.spi.DefaultOptionsMethodException;
import org.jboss.resteasy.util.HttpHeaderNames;
import org.junit.Before;
import org.junit.Test;
import org.xnap.commons.i18n.I18n;

import javax.servlet.http.HttpServletRequest;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;



/**
 * CandlepinExceptionMapperTest
 */
public class CandlepinExceptionMapperTest {

    private Injector injector;
    private HttpServletRequest req;
    private CandlepinExceptionMapper cem;
    private Logger logger;
    private Appender mockappender;


    @Before
    public void init() {
        MapperTestModule mtm = new MapperTestModule();
        injector = Guice.createInjector(mtm);
        req = injector.getInstance(HttpServletRequest.class);
        cem = injector.getInstance(CandlepinExceptionMapper.class);

        // prep the logger
        logger = Logger.getLogger(CandlepinExceptionMapper.class);
        mockappender = mock(Appender.class);
        logger.addAppender(mockappender);
        logger.setLevel(Level.DEBUG);
    }

    @Test
    public void toResponseBasicRuntimeException() {
        when(req.getHeader(HttpHeaderNames.ACCEPT)).thenReturn("application/json");

        Response r = cem.toResponse(new RuntimeException("test exception"));

        assertNotNull(r);
        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), r.getStatus());
        MultivaluedMap<String, Object> mm =  r.getMetadata();
        assertNotNull(mm);
        assertEquals(MediaType.APPLICATION_JSON_TYPE, mm.get("Content-Type").get(0));

        System.out.println(r.getEntity().getClass().getName());
        for (String key : mm.keySet()) {
            Object value = mm.getFirst(key);
            System.out.println("(k,v) = (" + key + ", " + value + ")");
        }
        verify(mockappender).doAppend(any(LoggingEvent.class));
        verifyMessage(r, rtmsg("test exception"));
    }

    @Test
    public void defaultOptionsException() {
        Response forex = mock(Response.class);
        when(req.getHeader(HttpHeaderNames.ACCEPT)).thenReturn("application/json");

        Response r = cem.toResponse(new DefaultOptionsMethodException("", forex));
        assertEquals(r, forex);
    }

    @Test
    public void nullAcceptHeader() {
        when(req.getHeader(HttpHeaderNames.ACCEPT)).thenReturn(null);
        Response r = cem.toResponse(new RuntimeException("null accept header"));
        assertNotNull(r);
        MultivaluedMap<String, Object> mm = r.getMetadata();
        assertNotNull(mm);
        assertEquals(MediaType.APPLICATION_XML_TYPE, mm.get("Content-Type").get(0));
        verifyMessage(r, rtmsg("null accept header"));
    }

    @Test
    public void candlepinException() {
        CandlepinException ce = mock(CandlepinException.class);
        when(ce.httpReturnCode()).thenReturn(Status.CONFLICT);
        when(ce.message()).thenReturn(
            new ExceptionMessage().setDisplayMessage("you screwed up"));
        when(req.getHeader(HttpHeaderNames.ACCEPT)).thenReturn("application/json");

        Response r = cem.toResponse(ce);
        assertNotNull(r);
        assertEquals(Status.CONFLICT.getStatusCode(), r.getStatus());
        verifyMessage(r, "you screwed up");
    }

    @Test
    public void childException() {
        when(req.getHeader(HttpHeaderNames.ACCEPT)).thenReturn("application/json");

        RuntimeException child = new RuntimeException("child ex");
        RuntimeException re = new RuntimeException("foobar", child);
        Response r = cem.toResponse(re);
        assertNotNull(r);
        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), r.getStatus());
        verifyMessage(r, rtmsg("child ex"));
    }

    private String rtmsg(String msg) {
        return "Runtime Error " + msg;
    }

    private void verifyMessage(Response r, String expectedmsg) {
        ExceptionMessage em = (ExceptionMessage) r.getEntity();

        assertNotNull(em);
        System.out.println(em.getDisplayMessage());
        assertTrue(expectedmsg,
            em.getDisplayMessage().startsWith(expectedmsg));
    }

    public static class MapperTestModule extends AbstractModule {

        @Override
        protected void configure() {
            bind(CandlepinExceptionMapper.class);
            bind(I18n.class).toProvider(I18nProvider.class);
            bind(HttpServletRequest.class).toInstance(mock(HttpServletRequest.class));
        }

    }
}
