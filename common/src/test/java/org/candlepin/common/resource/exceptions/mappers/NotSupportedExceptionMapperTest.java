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

import org.junit.jupiter.api.Test;

import javax.ws.rs.NotSupportedException;
import javax.ws.rs.core.Response;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * UnsupportedMediaTypeExceptionMapperTest
 */
public class NotSupportedExceptionMapperTest extends
    TestExceptionMapperBase {

    @Test
    public void handleException() {
        NotSupportedException nae =
            new NotSupportedException("unacceptable");
        NotSupportedExceptionMapper naem =
            injector.getInstance(NotSupportedExceptionMapper.class);
        Response r = naem.toResponse(nae);
        assertEquals(415, r.getStatus());
        verifyMessage(r, rtmsg("unacceptable"));
    }

    @Override
    public Class<?> getMapperClass() {
        return NotSupportedExceptionMapper.class;
    }
}
