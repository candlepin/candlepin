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
package org.fedoraproject.candlepin.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;

import org.junit.Test;

import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;

/**
 * Test Class for the Util class
 */
public class UtilTest {

    @Test
    public void testRandomUUIDS() {
        assertNotSame(Util.generateUUID(), Util.generateUUID());
    }

    @Test
    public void roundToMidnight() {
        Date now = new Date();
        Date midnight = Util.roundToMidnight(now);
        assertFalse(now.equals(midnight));

        Calendar cal = Calendar.getInstance();
        cal.setTime(midnight);
        assertEquals(23, cal.get(Calendar.HOUR_OF_DAY));
        assertEquals(59, cal.get(Calendar.MINUTE));
        assertEquals(59, cal.get(Calendar.SECOND));
        assertEquals(TimeZone.getDefault(), cal.getTimeZone());

        Date stillmidnight = Util.roundToMidnight(midnight);
        cal.setTime(stillmidnight);
        assertEquals(23, cal.get(Calendar.HOUR_OF_DAY));
        assertEquals(59, cal.get(Calendar.MINUTE));
        assertEquals(59, cal.get(Calendar.SECOND));
        assertEquals(TimeZone.getDefault(), cal.getTimeZone());
    }
}
