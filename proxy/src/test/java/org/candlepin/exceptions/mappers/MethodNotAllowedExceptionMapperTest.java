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

import org.jboss.resteasy.spi.MethodNotAllowedException;
import org.junit.Test;

import javax.ws.rs.core.Response;

/**
 * MethodNotAllowedExceptionMapperTest
 */
public class MethodNotAllowedExceptionMapperTest extends
    TestExceptionMapperBase {

    @Test
    public void handleMethodNotAllowed() {
        MethodNotAllowedException mnae = new MethodNotAllowedException("not allowed");
        MethodNotAllowedExceptionMapper mnaem =
            injector.getInstance(MethodNotAllowedExceptionMapper.class);
        Response r = mnaem.toResponse(mnae);
        assertEquals(405, r.getStatus());
        verifyMessage(r, rtmsg("not allowed"));
    }

    @Override
    public Class getMapperClass() {
        return MethodNotAllowedExceptionMapper.class;
    }
}
