/**
 * Copyright (c) 2009 - 2021 Red Hat, Inc.
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
package org.candlepin.resteasy;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.time.Instant;
import java.time.Month;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Locale;


public class DateFormatterTest {

    @AfterEach
    public void cleanup() {
        Locale.setDefault(Locale.US);
    }

    @Test
    public void testFromStringParseWithAustralianEnglishAsSystemLocale() {
        Locale.setDefault(new Locale("en", "AU"));
        DateFormatter formatter = new DateFormatter();
        Annotation[] annotations = new Annotation[] {Annotated.class.getAnnotations()[0]};
        formatter.setAnnotations(annotations);

        assertDoesNotThrow(() -> formatter.fromString("Thu, 01 Jan 1970 00:00:00 GMT"));
    }

    @Test
    public void testFromString() {
        DateFormatter formatter = new DateFormatter();
        Annotation[] annotations = new Annotation[] {Annotated.class.getAnnotations()[0]};
        formatter.setAnnotations(annotations);

        OffsetDateTime offsetDateTime =
            OffsetDateTime.of(2021, Month.NOVEMBER.getValue(), 26, 5, 0, 0, 0, ZoneOffset.UTC);

        Date parsedDate = formatter.fromString("Fri, 26 Nov 2021 05:00:00 UTC");

        assertEquals(offsetDateTime.toInstant(), parsedDate.toInstant());
    }

    @Test
    public void testFromStringParseWithSpecialValueNow() {
        DateFormatter formatter = new DateFormatter();
        Annotation[] annotations = new Annotation[] {Annotated.class.getAnnotations()[0]};
        formatter.setAnnotations(annotations);

        Date parsedDate = formatter.fromString("now");

        // Check that the returned date is more or less similar to the current time (rounded to minutes for
        // convenience)
        Instant currentDateRoundedDownToMinutes = new Date().toInstant().truncatedTo(ChronoUnit.MINUTES);
        Instant parsedDateRoundedDownToMinutes = parsedDate.toInstant().truncatedTo(ChronoUnit.MINUTES);
        assertEquals(currentDateRoundedDownToMinutes, parsedDateRoundedDownToMinutes);
    }

    // For the HTTP-date format, see https://httpwg.org/specs/rfc7231.html#http.date
    @Test
    public void testFromStringParseHTTPDateWithoutSpecifyingFormatThrowsException() {
        DateFormatter formatter = new DateFormatter();

        assertThrows(RuntimeException.class,
            () -> formatter.fromString("Thu, 01 Jan 1970 00:00:00 GMT"));
    }

    @Test
    public void testFromStringShouldParseISO8601DateWithoutSpecifyingFormat() {
        DateFormatter formatter = new DateFormatter();

        OffsetDateTime offsetDateTime =
            OffsetDateTime.of(2021, Month.NOVEMBER.getValue(), 26, 11, 32, 43, 0, ZoneOffset.UTC);

        Date parsedDate = formatter.fromString("2021-11-26T11:32:43+00:00");
        assertEquals(offsetDateTime.toInstant(), parsedDate.toInstant());
    }

    @Test
    public void testFromStringParseNullReturnsNull() {
        DateFormatter formatter = new DateFormatter();

        assertNull(formatter.fromString(null));
    }

    @Test
    public void testFromStringParseEmptyReturnsNull() {
        DateFormatter formatter = new DateFormatter();

        assertNull(formatter.fromString(""));
    }

    @Test
    public void testFromStringParseInvalidDateThrowsException() {
        DateFormatter formatter = new DateFormatter();
        Annotation[] annotations = new Annotation[] {Annotated.class.getAnnotations()[0]};
        formatter.setAnnotations(annotations);

        // use wrong format as input (missing timezone code)
        assertThrows(RuntimeException.class, () -> formatter.fromString("Thu, 01 Jan 1970 00:00:00"));
    }

    /*
     * Mock class with annotation that we need to test with
     */
    @DateFormat("EEE, dd MMM yyyy HH:mm:ss z")
    private static class Annotated {}
}
