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
package org.candlepin.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.candlepin.test.TestUtil;

import org.junit.Test;

import java.util.Date;



/**
 * DateRangeTest
 */
public class DateRangeTest {

    @Test
    public void getters() {
        Date start = TestUtil.createDate(2012, 5, 22);
        Date end = TestUtil.createDate(2012, 7, 4);
        DateRange range = new DateRange(start, end);

        assertEquals(start, range.getStartDate());
        assertEquals(end, range.getEndDate());
    }

    @Test
    public void contains() {
        DateRange range = new DateRange(TestUtil.createDate(2001, 7, 5), TestUtil.createDate(2010, 7, 4));

        assertTrue(range.contains(TestUtil.createDate(2005, 6, 9)));
        assertFalse(range.contains(TestUtil.createDate(1971, 7, 19)));
        assertFalse(range.contains(TestUtil.createDate(2012, 4, 19)));
        assertTrue(range.contains(TestUtil.createDate(2001, 7, 5)));
        assertTrue(range.contains(TestUtil.createDate(2010, 7, 4)));
    }

    @Test
    public void string() {
        DateRange range = new DateRange(TestUtil.createDate(2012, 5, 22), TestUtil.createDate(2013, 7, 4));

        String result = range.toString();

        String dateFormat = "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}.\\d{3}[-+]\\d{4}";
        String expectedRegex = String.format("DateRange \\[%1$s - %1$s\\]", dateFormat);

        assertTrue(String.format("\"%s\" did not match the expected format: %s", result, expectedRegex),
            result.matches(expectedRegex));
    }
}
