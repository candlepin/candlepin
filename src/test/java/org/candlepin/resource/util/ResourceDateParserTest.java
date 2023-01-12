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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.exceptions.BadRequestException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * ResourceDateParserTest
 */
public class ResourceDateParserTest {

    private I18n i18n;

    @BeforeEach
    void setUp() {
        this.i18n = I18nFactory.getI18n(getClass(), Locale.US, I18nFactory.FALLBACK);
    }

    @Test
    public void parseDate() {
        Date parsed = ResourceDateParser.parseDateString(this.i18n, "2012-05-29");

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
        Date parsed = ResourceDateParser.parseDateString(this.i18n, "1997-07-16T19:20:30-00:00");

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
        assertNull(ResourceDateParser.parseDateString(this.i18n, null));
        assertNull(ResourceDateParser.parseDateString(this.i18n, ""));
        assertNull(ResourceDateParser.parseDateString(this.i18n, "    "));
    }

    @Test
    public void parseDateError() {
        assertThrows(BadRequestException.class,
            () -> ResourceDateParser.parseDateString(this.i18n, "2012/13/64"));
    }
}
