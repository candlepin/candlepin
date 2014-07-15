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
package org.candlepin.pinsetter.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.junit.Test;


/**
 * PinsetterExceptionTest
 * @version $Rev$
 */
public class PinsetterExceptionTest {

    @Test
    public void testMessageCtor() {
        PinsetterException pe = new PinsetterException("message");
        assertNotNull(pe);
        assertEquals("message", pe.getMessage());
        assertNull(pe.getCause());
    }

    @Test
    public void testMessageCauseCtor() {
        RuntimeException re = new RuntimeException("fake exception");
        PinsetterException pe = new PinsetterException("message", re);
        assertNotNull(pe);
        assertEquals("message", pe.getMessage());
        assertNotNull(pe.getCause());
        assertEquals(re, pe.getCause());
        assertEquals("fake exception", pe.getCause().getMessage());
    }
}
