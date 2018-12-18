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

import javax.ws.rs.NotAllowedException;
import javax.ws.rs.core.Response;

/**
 * MethodNotAllowedExceptionMapperTest
 */
public class NotAllowedExceptionMapperTest extends
    TestExceptionMapperBase {

    @Test
    public void handleMethodNotAllowed() {
        NotAllowedException mnae = new NotAllowedException("Not Allowed");
        NotAllowedExceptionMapper mnaem =
            injector.getInstance(NotAllowedExceptionMapper.class);
        Response r = mnaem.toResponse(mnae);
        assertEquals(405, r.getStatus());
        verifyMessage(r, rtmsg(".+ Not Allowed.*"));
    }

    @Override
    public Class<?> getMapperClass() {
        return NotAllowedExceptionMapper.class;
    }
}
