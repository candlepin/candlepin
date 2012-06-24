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
package org.candlepin.resource.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.candlepin.exceptions.BadRequestException;
import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * ResourceDateParserTest
 */
public class ResourceDateParserTest {

    @Test(expected = BadRequestException.class)
    public void errorFromAndToPlusDays() {
        ResourceDateParser.getFromDate("2012/01/01", "2012/10/10", "3");
    }

    @Test(expected = BadRequestException.class)
    public void errorFromPlusDays() {
        ResourceDateParser.getFromDate("2012/01/01", null, "3");
    }

    @Test(expected = BadRequestException.class)
    public void errorToPlusDays() {
        ResourceDateParser.getFromDate(null, "2012/01/01", "3");
    }

    @Test
    public void onlyDays() {
        Calendar cal = Calendar.getInstance();
        // the 3 here should match the value given to getFromDate
        // below.
        cal.add(Calendar.DATE, -3);
        int expected = cal.get(Calendar.DATE);
        Date from = ResourceDateParser.getFromDate(null, null, "3");
        cal.setTime(from);
        assertEquals(expected, cal.get(Calendar.DATE));
    }

    @Test
    public void allEmpty() {
        assertNull(ResourceDateParser.getFromDate(null, null, null));
        assertNull(ResourceDateParser.getFromDate("", "", ""));
        assertNull(ResourceDateParser.getFromDate(null, null, ""));
        assertNull(ResourceDateParser.getFromDate("    ", "    ", "    "));
        assertNull(ResourceDateParser.getFromDate("", "", null));
    }

    @Test
    public void fromAndTo() {
        Date from = ResourceDateParser.getFromDate("2012-01-01",
            "2012-01-10", null);

        Calendar cal = Calendar.getInstance();
        cal.setTime(from);
        assertEquals(1, cal.get(Calendar.DATE));
    }

    @Test(expected = NumberFormatException.class)
    public void nonNumberForDays() {
        ResourceDateParser.getFromDate(null, null, "ABC");
    }

    @Test
    public void parseDate() {
        Date parsed = ResourceDateParser.parseDateString("2012-05-29");

        Calendar cal = Calendar.getInstance();
        cal.setTime(parsed);
        // adding one because get returns 0 based month's which to me
        // is confusing, January should always be 1 not 0 :)
        assertEquals(5, cal.get(Calendar.MONTH) + 1);
        assertEquals(29, cal.get(Calendar.DATE));
        assertEquals(2012, cal.get(Calendar.YEAR));
    }

    @Test
    public void parseDateTime() {
        Date parsed = ResourceDateParser.parseDateString(
            "1997-07-16T19:20:30-00:00");

        Calendar cal = Calendar.getInstance(TimeZone.getTimeZone("GMT"));
        cal.setTime(parsed);
        // adding one because get returns 0 based month's which to me
        // is confusing, January should always be 1 not 0 :)
        assertEquals(7, cal.get(Calendar.MONTH) + 1);
        assertEquals(16, cal.get(Calendar.DATE));
        assertEquals(1997, cal.get(Calendar.YEAR));
        assertEquals(7, cal.get(Calendar.HOUR));
        assertEquals(20, cal.get(Calendar.MINUTE));
        assertEquals(30, cal.get(Calendar.SECOND));
    }

    @Test
    public void nullParseDate() {
        assertEquals(null, ResourceDateParser.parseDateString(null));
        assertEquals(null, ResourceDateParser.parseDateString(""));
        assertEquals(null, ResourceDateParser.parseDateString("    "));
    }

    @Test(expected = BadRequestException.class)
    public void parseDateError() {
        ResourceDateParser.parseDateString("2012/13/64");
    }
}
