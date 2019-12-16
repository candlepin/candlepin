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
package org.candlepin.test;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Date;

import static org.junit.Assert.assertEquals;

/**
 * Utilitiy methods used in tests
 */
public class TestDateUtil {

    private TestDateUtil() {
        // utility ctor
    }

    public static Date date(int year, int month, int day) {
        Calendar calendar = Calendar.getInstance();
        calendar.clear();
        calendar.set(year, month - 1, day, 0, 0, 0);
        return calendar.getTime();
    }

    /**
     * Assert two dates are equal with variance allowed within a given ChronoUnit.  E.g. Two instants that are
     * only milliseconds apart within the same second would pass the assertion if ChronoUnit.SECONDS (or
     * higher) was passed in.  For practical purposes, ChronoUnit.HOURS is as low as you'd want to go since
     * using ChronoUnit.MINUTES can result in sporadic failures when the two times straddle the 60th second
     * of a minute.  The same can happen with ChronoUnit.HOURS but much more rarely.
     * @param expected expected value
     * @param actual actual value
     * @param fuzz threshold of variance to allow
     */
    public static void assertDatesMatch(Date expected, Date actual, ChronoUnit fuzz) {
        ZonedDateTime zonedExpected = expected
            .toInstant()
            .atZone(ZoneId.systemDefault())
            .truncatedTo(fuzz);

        ZonedDateTime zonedActual = actual
            .toInstant()
            .atZone(ZoneId.systemDefault())
            .truncatedTo(fuzz);

        assertEquals(zonedExpected, zonedActual);
    }
}
