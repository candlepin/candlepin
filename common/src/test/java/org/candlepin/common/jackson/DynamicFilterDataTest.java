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
package org.candlepin.common.jackson;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;



/**
 * DynamicFilterDataTest
 */
@ExtendWith(MockitoExtension.class)
public class DynamicFilterDataTest {

    @Test
    public void testSimpleWhitelistFiltering() {
        DynamicFilterData filterData = new DynamicFilterData(true);
        filterData.includeAttribute("bacon");

        assertTrue(filterData.isAttributeExcluded("spinach"));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("spinach")));
        assertFalse(filterData.isAttributeExcluded("bacon"));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("bacon")));
    }

    @Test
    public void testMultiLevelWhitelistFiltering() {
        DynamicFilterData filterData = new DynamicFilterData(true);
        filterData.includeAttribute("bacon.egg");

        assertTrue(filterData.isAttributeExcluded("spinach"));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("spinach")));
        assertFalse(filterData.isAttributeExcluded("bacon"));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("bacon")));

        assertTrue(filterData.isAttributeExcluded("bacon.spinach"));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("bacon", "spinach")));
        assertTrue(filterData.isAttributeExcluded("spinach.bacon"));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("spinach", "bacon")));
        assertFalse(filterData.isAttributeExcluded("bacon.egg"));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("bacon", "egg")));

        assertTrue(filterData.isAttributeExcluded("spinach.bacon.egg.cheese"));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("spinach", "bacon", "egg", "cheese")));
        assertFalse(filterData.isAttributeExcluded("bacon.egg.cheese"));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("bacon", "egg", "cheese")));
    }

    @Test
    public void testSimpleBlacklistFiltering() {
        DynamicFilterData filterData = new DynamicFilterData(false);
        filterData.excludeAttribute("bacon");

        assertFalse(filterData.isAttributeExcluded("spinach"));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("spinach")));
        assertTrue(filterData.isAttributeExcluded("bacon"));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("bacon")));
    }

    @Test
    public void testMultiLevelBlacklistFiltering() {
        DynamicFilterData filterData = new DynamicFilterData(false);
        filterData.excludeAttribute("bacon.egg");

        assertFalse(filterData.isAttributeExcluded("spinach"));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("spinach")));
        assertFalse(filterData.isAttributeExcluded("bacon"));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("bacon")));

        assertFalse(filterData.isAttributeExcluded("bacon.spinach"));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("bacon", "spinach")));
        assertFalse(filterData.isAttributeExcluded("spinach.bacon"));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("spinach", "bacon")));
        assertTrue(filterData.isAttributeExcluded("bacon.egg"));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("bacon", "egg")));

        assertFalse(filterData.isAttributeExcluded("spinach.bacon.egg.cheese"));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("spinach", "bacon", "egg", "cheese")));
        assertTrue(filterData.isAttributeExcluded("bacon.egg.cheese"));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("bacon", "egg", "cheese")));
    }

    @Test
    public void testMultiLevelDualFiltering() {
        DynamicFilterData filterData = new DynamicFilterData(false);
        filterData.includeAttribute("a.b1");
        filterData.includeAttribute("a.b2.d2");
        filterData.excludeAttribute("a.b1.c2");
        filterData.excludeAttribute("a.b2");

        // a: { b1: { c1, c2, c3 }, b2: { d1, d2, d3 } }

        assertFalse(filterData.isAttributeExcluded("a"));
        assertFalse(filterData.isAttributeExcluded("a.b1"));
        assertFalse(filterData.isAttributeExcluded("a.b1.c1"));
        assertTrue(filterData.isAttributeExcluded("a.b1.c2"));
        assertFalse(filterData.isAttributeExcluded("a.b1.c3"));
        assertFalse(filterData.isAttributeExcluded("a.b2"));
        assertTrue(filterData.isAttributeExcluded("a.b2.d1"));
        assertFalse(filterData.isAttributeExcluded("a.b2.d2"));
        assertTrue(filterData.isAttributeExcluded("a.b2.d3"));

        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b1", "c1")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a", "b1", "c2")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b1", "c3")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a", "b2", "d1")));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("a", "b2", "d2")));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("a", "b2", "d3")));
    }

}
