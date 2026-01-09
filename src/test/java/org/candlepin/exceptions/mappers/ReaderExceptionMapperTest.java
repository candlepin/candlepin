/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.jboss.resteasy.spi.ReaderException;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.DatabindException;

import javax.ws.rs.core.Response;


/**
 * ReaderExceptionMapperTest
 */
public class ReaderExceptionMapperTest extends TestExceptionMapperBase {

    @Test
    public void handleExceptionWithoutResponse() {
        ReaderException nfe = new ReaderException("unacceptable");
        ReaderExceptionMapper nfem = injector.getInstance(ReaderExceptionMapper.class);
        Response r = nfem.toResponse(nfe);
        assertEquals(500, r.getStatus());
        verifyMessage(r, rtmsg("unacceptable"));
    }

    @Test
    public void handleExceptionWithResponse() {
        Response mockr = mock(Response.class);
        when(mockr.getStatus()).thenReturn(400);
        ReaderException nfe = new ReaderException("unacceptable", mockr);
        ReaderExceptionMapper nfem = injector.getInstance(ReaderExceptionMapper.class);
        Response r = nfem.toResponse(nfe);
        assertEquals(400, r.getStatus());
        verifyMessage(r, rtmsg("unacceptable"));
    }

    @Test
    public void handleJsonMappingExceptionWithResponse() {
        Response mockr = mock(Response.class);
        when(mockr.getStatus()).thenReturn(400);
        ReaderException nfe = new ReaderException("kaboom", mock(DatabindException.class));
        ReaderExceptionMapper nfem = injector.getInstance(ReaderExceptionMapper.class);
        Response r = nfem.toResponse(nfe);
        assertEquals(400, r.getStatus());
        verifyMessage(r, rtmsg("kaboom"));
    }

    @Override
    public Class<?> getMapperClass() {
        return ReaderExceptionMapper.class;
    }
}
