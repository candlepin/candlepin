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
package org.candlepin.model.test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.candlepin.model.Release;
import org.junit.Test;

/**
 * ReleaseTest
 */
public class ReleaseTest {
    @Test
    public void testEquals() {
        Release rl = new Release("24.1");
        Release rl1 = new Release("24.1");
        Release rl2 = new Release("18.9");

        assertTrue(rl.equals(rl1));
        assertFalse(rl.equals(rl2));
        assertFalse(rl.equals("345345"));

        Release rlE = new Release("");
        Release rlE2 = new Release("");

        assertTrue(rlE.equals(rlE2));
        assertTrue(rl.equals(rl));
        assertFalse(rl.equals(rlE));
    }

    @Test
    public void testNullEquals() {
        Release r = new Release(null);
        Release r1 = new Release("2.0");

        assertFalse(r.equals(r1));
        assertFalse(r1.equals(r));
        assertTrue(r.equals(r));
        assertTrue(r1.equals(r1));
    }
}
