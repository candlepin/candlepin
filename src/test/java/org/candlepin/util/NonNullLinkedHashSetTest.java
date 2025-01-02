/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

public class NonNullLinkedHashSetTest {

    @Test
    public void testAddWithNullElement() {
        NonNullLinkedHashSet<String> set = new NonNullLinkedHashSet<>();

        assertThrows(IllegalArgumentException.class, () -> {
            set.add(null);
        });
    }

    @Test
    public void testAdd() {
        NonNullLinkedHashSet<String> set = new NonNullLinkedHashSet<>();
        String element1 = "element-1";
        set.add(element1);

        assertThat(set)
            .hasSize(1)
            .containsExactly(element1);

        String element2 = "element-2";
        set.add(element2);

        assertThat(set)
            .hasSize(2)
            .containsExactlyElementsOf(List.of(element1, element2));
    }

    @Test
    public void testAddAllWithNullElement() {
        NonNullLinkedHashSet<String> set = new NonNullLinkedHashSet<>();

        List<String> list = new ArrayList<>();
        list.add("element-1");
        list.add(null);
        list.add("element-3");

        assertThrows(IllegalArgumentException.class, () -> {
            set.addAll(list);
        });
    }

    @Test
    public void testAddAll() {
        NonNullLinkedHashSet<String> set = new NonNullLinkedHashSet<>();

        List<String> list = new ArrayList<>();
        list.add("element-1");
        list.add("element-2");
        list.add("element-3");

        boolean updated = set.addAll(list);

        assertTrue(updated);
        assertThat(set)
            .hasSize(list.size())
            .containsExactlyElementsOf(list);
    }

}
