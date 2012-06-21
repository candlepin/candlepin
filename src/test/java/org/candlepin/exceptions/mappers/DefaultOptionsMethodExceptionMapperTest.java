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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jboss.resteasy.spi.DefaultOptionsMethodException;
import org.junit.Test;

import javax.ws.rs.core.Response;
/**
 * DefaultOptionsMethodExceptionMapperTest
 */
public class DefaultOptionsMethodExceptionMapperTest extends
    TestExceptionMapperBase {

    @Test
    public void exceptionWithResponse() {
        Response mockr = mock(Response.class);
        when(mockr.getStatus()).thenReturn(500);
        DefaultOptionsMethodException dome =
            new DefaultOptionsMethodException("oops", mockr);
        DefaultOptionsMethodExceptionMapper domem =
            injector.getInstance(DefaultOptionsMethodExceptionMapper.class);
        Response r = domem.toResponse(dome);
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), r.getStatus());
        assertEquals(mockr, r);
        assertEquals("oops", dome.getMessage());
    }

    @Test
    public void verifyResponse() {
        DefaultOptionsMethodException dome =
            new DefaultOptionsMethodException("oops", null);
        DefaultOptionsMethodExceptionMapper domem =
            injector.getInstance(DefaultOptionsMethodExceptionMapper.class);
        Response r = domem.toResponse(dome);
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), r.getStatus());
        verifyMessage(r, rtmsg("oops"));
    }

    @Override
    public Class getMapperClass() {
        return DefaultOptionsMethodExceptionMapper.class;
    }
}
