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

import org.junit.jupiter.api.Test;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

/**
 * WebApplicationExceptionMapper
 */
public class WebApplicationExceptionMapperTest extends TestExceptionMapperBase {

    @Test
    public void handleExceptionWithResponse() {
        Response mockr = mock(Response.class);
        when(mockr.getStatus()).thenReturn(500);
        when(mockr.getStatusInfo()).thenReturn(Status.INTERNAL_SERVER_ERROR);

        WebApplicationException nfe = new WebApplicationException(mockr);
        WebApplicationExceptionMapper nfem =
            injector.getInstance(WebApplicationExceptionMapper.class);
        Response r = nfem.toResponse(nfe);
        assertEquals(500, r.getStatus());
        verifyMessage(r, rtmsg(""));
    }

    @Override
    public Class<?> getMapperClass() {
        return WebApplicationExceptionMapper.class;
    }
}
