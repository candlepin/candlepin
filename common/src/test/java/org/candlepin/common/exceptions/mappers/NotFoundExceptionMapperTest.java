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

import org.junit.jupiter.api.Test;

import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

/**
 * NotFoundExceptionMapperTest
 */
public class NotFoundExceptionMapperTest extends TestExceptionMapperBase {

    @Test
    public void handleNotFoundException() {
        NotFoundException nfe = new NotFoundException("unacceptable");
        NotFoundExceptionMapper nfem = injector.getInstance(NotFoundExceptionMapper.class);
        Response r = nfem.toResponse(nfe);
        assertEquals(404, r.getStatus());
        verifyMessage(r, rtmsg("unacceptable"));
    }

    @Override
    public Class<?> getMapperClass() {
        return NotFoundExceptionMapper.class;
    }

    /**
     * With RestEASY 3 the NotFoundException is also thrown for wrong parameters
     * of JAX-RS resources
     */
    @Test
    public void handleNotFoundQueryParameterException() {
        NotFoundExceptionMapper nfem = injector.getInstance(NotFoundExceptionMapper.class);
        String foo = "javax.ws.rs.SomeThing(\"paramName\") value is 'strVal' for";
        NotFoundException bre = new NotFoundException(foo);
        Response r = nfem.toResponse(bre);

        assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        verifyMessage(r, "strVal is not a valid value for paramName");
    }
}
