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
package org.fedoraproject.candlepin.model.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.fedoraproject.candlepin.model.Status;

import org.junit.Before;
import org.junit.Test;


/**
 * StatusTest
 */
public class StatusTest {
    private Status status;

    @Before
    public void init() {
        status = new Status(Boolean.TRUE, "1.0", "2", Boolean.TRUE);
    }

    @Test
    public void result() {
        assertTrue(status.getResult().booleanValue());
        status.setResult(Boolean.FALSE);
        assertFalse(status.getResult().booleanValue());
    }

    @Test
    public void version() {
        assertEquals("1.0", status.getVersion());
        status.setVersion("0.1");
        assertEquals("0.1", status.getVersion());
    }

    @Test
    public void release() {
        assertEquals("2", status.getRelease());
        status.setRelease("3");
        assertEquals("3", status.getRelease());
    }

    @Test
    public void standalone() {
        assertEquals(Boolean.TRUE, status.getStandalone());
        status.setStandalone(Boolean.FALSE);
        assertEquals(Boolean.FALSE, status.getStandalone());
    }
}
