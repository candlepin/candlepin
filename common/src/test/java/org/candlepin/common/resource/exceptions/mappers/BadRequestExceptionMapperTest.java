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
package org.candlepin.common.resource.exceptions.mappers;

import org.jboss.resteasy.spi.BadRequestException;
import org.junit.jupiter.api.Test;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * BadRequestExceptionMapperTest
 */
public class BadRequestExceptionMapperTest extends TestExceptionMapperBase {
    private BadRequestExceptionMapper rem;

    @Test
    public void extractIllegalValue() {
        rem = injector.getInstance(BadRequestExceptionMapper.class);
        String foo = "javax.ws.rs.SomeThing(\"paramName\") value is 'strVal' for";
        BadRequestException bre = new BadRequestException(foo);
        Response r = rem.toResponse(bre);

        assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        verifyMessage(r, "strVal is not a valid value for paramName");
    }

    @Test
    public void emptyMessage() {
        rem = injector.getInstance(BadRequestExceptionMapper.class);
        BadRequestException bre = new BadRequestException("");
        Response r = rem.toResponse(bre);

        assertEquals(Status.BAD_REQUEST.getStatusCode(), r.getStatus());
        verifyMessage(r, "Bad Request");
    }

    @Override
    public Class<?> getMapperClass() {
        return BadRequestExceptionMapper.class;
    }
}
