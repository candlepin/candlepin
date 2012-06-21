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

import org.jboss.resteasy.spi.ApplicationException;
import org.junit.Test;

import java.io.EOFException;

import javax.ws.rs.core.Response;

/**
 * ApplicationExceptionMapperTest
 */
public class ApplicationExceptionMapperTest extends TestExceptionMapperBase {

    @Test
    public void withCause() {
        EOFException eofe = new EOFException("screwed");
        ApplicationException ae = new ApplicationException("oops", eofe);
        ApplicationExceptionMapper aem =
            injector.getInstance(ApplicationExceptionMapper.class);
        Response r = aem.toResponse(ae);
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), r.getStatus());
        verifyMessage(r, rtmsg("oops"));
    }

    @Test
    public void noCause() {
        ApplicationException ae = new ApplicationException("oops", null);
        ApplicationExceptionMapper aem =
            injector.getInstance(ApplicationExceptionMapper.class);
        Response r = aem.toResponse(ae);
        assertEquals(Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(), r.getStatus());
        verifyMessage(r, rtmsg("oops"));
    }

    @Override
    public Class getMapperClass() {
        return ApplicationExceptionMapper.class;
    }
}
