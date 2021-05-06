/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Before;
import org.junit.Test;

import java.util.HashSet;
import java.util.Set;


/**
 * Test suite for the SetView class
 */
public class SetViewTest extends CollectionViewTest {

    @Before
    @Override
    public void init() {
        this.source = new HashSet();
        this.testobj = new SetView((Set) this.source);
    }

    @Test
    public void testEquals() {
        Set comp = new HashSet();

        assertTrue(this.testobj.equals(comp));

        for (int i = 0; i < 5; ++i) {
            this.source.add(String.valueOf(i));
            assertFalse(this.testobj.equals(comp));

            comp.add(String.valueOf(i));
            assertTrue(this.testobj.equals(comp));
        }

        this.source.clear();
        assertFalse(this.testobj.equals(comp));

        comp.clear();
        assertTrue(this.testobj.equals(comp));
    }

    @Test
    public void testHashCode() {
        Set comp = new HashSet();

        assertEquals(comp.hashCode(), this.testobj.hashCode());

        for (int i = 0; i < 5; ++i) {
            this.source.add(String.valueOf(i));
            assertNotEquals(comp.hashCode(), this.testobj.hashCode());

            comp.add(String.valueOf(i));
            assertEquals(comp.hashCode(), this.testobj.hashCode());
        }

        this.source.clear();
        assertNotEquals(comp.hashCode(), this.testobj.hashCode());

        comp.clear();
        assertEquals(comp.hashCode(), this.testobj.hashCode());
    }

}
