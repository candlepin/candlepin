/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;


public class SetViewTest extends CollectionViewTest {

    @BeforeEach
    @Override
    public void init() {
        this.source = new HashSet();
        this.testobj = new SetView((Set) this.source);
    }

    @Test
    public void testEquals() {
        Set comp = new HashSet();

        assertEquals(this.testobj, comp);

        for (int i = 0; i < 5; ++i) {
            this.source.add(String.valueOf(i));
            assertNotEquals(this.testobj, comp);

            comp.add(String.valueOf(i));
            assertEquals(this.testobj, comp);
        }

        this.source.clear();
        assertNotEquals(this.testobj, comp);

        comp.clear();
        assertEquals(this.testobj, comp);
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
