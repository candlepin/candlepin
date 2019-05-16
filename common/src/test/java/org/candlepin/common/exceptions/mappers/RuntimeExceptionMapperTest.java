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
package org.candlepin.common.exceptions.mappers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.candlepin.common.exceptions.CandlepinException;
import org.candlepin.common.exceptions.ConflictException;
import org.candlepin.common.exceptions.ExceptionMessage;
import org.candlepin.common.util.VersionUtil;

import org.jboss.resteasy.spi.BadRequestException;
import org.jboss.resteasy.spi.DefaultOptionsMethodException;
import org.jboss.resteasy.util.HttpHeaderNames;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URISyntaxException;

import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;


/**
 * RuntimeExceptionMapperTest
 */
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

    @Disabled
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
    public Class<?> getMapperClass() {
        return RuntimeExceptionMapper.class;
    }
}
