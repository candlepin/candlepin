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
package org.candlepin.exceptions.mappers;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.exceptions.CandlepinException;
import org.candlepin.exceptions.ExceptionMessage;
import org.candlepin.sync.ImportExtractionException;
import org.candlepin.util.VersionUtil;
import org.jboss.resteasy.spi.BadRequestException;
import org.jboss.resteasy.spi.DefaultOptionsMethodException;
import org.jboss.resteasy.util.HttpHeaderNames;
import org.junit.Ignore;
import org.junit.Test;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;


/**
 * RuntimeExceptionMapperTest
 */
public class RuntimeExceptionMapperTest extends TestExceptionMapperBase {

    @Test
    public void toResponseBasicRuntimeException() {
        when(req.getHeader(HttpHeaderNames.ACCEPT)).thenReturn("application/json");

        Response r = rem.toResponse(new RuntimeException("test exception"));

        assertNotNull(r);
        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), r.getStatus());
        MultivaluedMap<String, Object> mm =  r.getMetadata();
        assertNotNull(mm);
        assertEquals(MediaType.APPLICATION_JSON_TYPE, mm.get("Content-Type").get(0));
        assertEquals("${version}-${release}",
            r.getMetadata().get(VersionUtil.VERSION_HEADER).get(0));
        verifyMessage(r, rtmsg("test exception"));
    }

    @Ignore
    @Test
    public void defaultOptionsException() {
        Response forex = mock(Response.class);
        when(req.getHeader(HttpHeaderNames.ACCEPT)).thenReturn("application/json");

        Response r = rem.toResponse(new DefaultOptionsMethodException("", forex));
        assertEquals(forex, r);
    }

    @Test
    public void nullAcceptHeader() {
        when(req.getHeader(HttpHeaderNames.ACCEPT)).thenReturn(null);
        Response r = rem.toResponse(new RuntimeException("null accept header"));
        assertNotNull(r);
        MultivaluedMap<String, Object> mm = r.getMetadata();
        assertNotNull(mm);
        assertEquals(MediaType.APPLICATION_JSON_TYPE, mm.get("Content-Type").get(0));
        assertEquals("${version}-${release}",
            r.getMetadata().get(VersionUtil.VERSION_HEADER).get(0));
        verifyMessage(r, rtmsg("null accept header"));
    }

    @Test
    public void candlepinException() {
        CandlepinException ce = mock(CandlepinException.class);
        when(ce.httpReturnCode()).thenReturn(Status.CONFLICT);
        when(ce.message()).thenReturn(
            new ExceptionMessage().setDisplayMessage("you screwed up"));
        when(req.getHeader(HttpHeaderNames.ACCEPT)).thenReturn("application/json");

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
        when(ce.getCause()).thenReturn(new ImportExtractionException("Error"));
        Response r = rem.toResponse(ce);
        assertNotNull(r);
        assertEquals(Status.CONFLICT.getStatusCode(), r.getStatus());
        verifyMessage(r, "you screwed up");
    }

    @Test
    public void childException() {
        when(req.getHeader(HttpHeaderNames.ACCEPT)).thenReturn("application/json");

        RuntimeException child = new RuntimeException("child ex");
        RuntimeException re = new RuntimeException("foobar", child);
        Response r = rem.toResponse(re);
        assertNotNull(r);
        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), r.getStatus());
        verifyMessage(r, rtmsg("child ex"));
    }

    @Test
    public void jbossBadRequestException() {
        String foo = "foo";
        BadRequestException bre = new BadRequestException(foo);
        Response r = rem.toResponse(bre);

        assertEquals(Status.INTERNAL_SERVER_ERROR.getStatusCode(), r.getStatus());
        verifyMessage(r, "Runtime Error");
    }

    @Override
    public Class getMapperClass() {
        return RuntimeExceptionMapper.class;
    }
}
