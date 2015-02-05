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

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Arrays;



/**
 * DynamicFilterDataTest
 */
@RunWith(MockitoJUnitRunner.class)
public class DynamicFilterDataTest {

    @Test
    public void testSimpleWhitelistFiltering() {
        DynamicFilterData filterData = new DynamicFilterData(false);
        filterData.addAttributeFilter("bacon");

        assertTrue(filterData.isAttributeExcluded("spinach"));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("spinach")));
        assertFalse(filterData.isAttributeExcluded("bacon"));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("bacon")));
    }

    @Test
    public void testMultiLevelWhitelistFiltering() {
        DynamicFilterData filterData = new DynamicFilterData(false);
        filterData.addAttributeFilter("bacon.egg");

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
        DynamicFilterData filterData = new DynamicFilterData(true);
        filterData.addAttributeFilter("bacon");

        assertFalse(filterData.isAttributeExcluded("spinach"));
        assertFalse(filterData.isAttributeExcluded(Arrays.asList("spinach")));
        assertTrue(filterData.isAttributeExcluded("bacon"));
        assertTrue(filterData.isAttributeExcluded(Arrays.asList("bacon")));
    }

    @Test
    public void testMultiLevelBlacklistFiltering() {
        DynamicFilterData filterData = new DynamicFilterData(true);
        filterData.addAttributeFilter("bacon.egg");

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
}
