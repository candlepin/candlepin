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
package org.candlepin.resource.util;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.candlepin.test.TestDateUtil;
import org.junit.Test;

import java.util.Date;
/**
 * DateRangeTest
 */
public class DateRangeTest {
    private DateRange range;

    @Test
    public void getters() {
        Date start = TestDateUtil.date(2012, 5, 22);
        Date end = TestDateUtil.date(2012, 7, 4);
        range = new DateRange(start, end);

        assertEquals(start, range.getStartDate());
        assertEquals(end, range.getEndDate());
    }

    @Test
    public void contains() {
        range = new DateRange(TestDateUtil.date(2001, 7, 5),
            TestDateUtil.date(2010, 7, 4));

        assertTrue(range.contains(TestDateUtil.date(2005, 6, 9)));
        assertFalse(range.contains(TestDateUtil.date(1971, 7, 19)));
        assertFalse(range.contains(TestDateUtil.date(2012, 4, 19)));
        assertTrue(range.contains(TestDateUtil.date(2001, 7, 5)));
        assertTrue(range.contains(TestDateUtil.date(2010, 7, 4)));
    }

    @Test
    public void string() {
        range = new DateRange(TestDateUtil.date(2012, 5, 22),
            TestDateUtil.date(2012, 7, 4));

        String result = range.toString();
        assertTrue(result.contains("Start:"));
        assertTrue(result.contains("End:"));
        assertTrue(result.contains("2012"));
    }
}
