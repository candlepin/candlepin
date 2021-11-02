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
package org.candlepin.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;


public class ReleaseTest {
    @Test
    public void testEquals() {
        Release rl = new Release("24.1");
        Release rl1 = new Release("24.1");
        Release rl2 = new Release("18.9");

        assertEquals(rl, rl1);
        assertNotEquals(rl, rl2);
        assertNotEquals("345345", rl);

        Release rlE = new Release("");
        Release rlE2 = new Release("");

        assertEquals(rlE, rlE2);
        assertEquals(rl, rl);
        assertNotEquals(rl, rlE);
    }

    @Test
    public void testNullEquals() {
        Release r = new Release(null);
        Release r1 = new Release("2.0");

        assertNotEquals(r, r1);
        assertNotEquals(r1, r);
        assertEquals(r, r);
        assertEquals(r1, r1);
    }
}
