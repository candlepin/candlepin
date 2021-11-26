/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
package org.candlepin.resteasy.converter;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.time.OffsetDateTime;
import java.util.Date;
import java.util.Locale;

import javax.validation.constraints.Pattern;
import javax.ws.rs.ext.ParamConverter;


public class OffsetDateTimeParamConverterProviderTest {

    @AfterEach
    public void tearDown() {
        Locale.setDefault(Locale.US);
    }

    @Test
    public void testNowValueParse() {
        ParamConverter<OffsetDateTime> converter = this.createConverter(null);
        OffsetDateTime dateTime = converter.fromString("now");
        assertNotNull(dateTime);
    }

    @Test
    public void testDateStringConverterWithPattern() {
        Annotation[] annotations = new Annotation[] {Annotated.class.getAnnotations()[0]};
        ParamConverter<OffsetDateTime> converter = this.createConverter(annotations);
        OffsetDateTime dateTime = converter.fromString("Fri, 13 Mar 2020 13:30:30 EST");
        assertEquals("2020-03-13T13:30:30-04:00", dateTime.toString());
    }

    @Test
    public void testDateStringConverterWithoutPattern() {
        ParamConverter<OffsetDateTime> converter = this.createConverter(null);
        OffsetDateTime dateTime = converter.fromString("2021-01-24T13:30:30.382+01:00");
        assertEquals("2021-01-24T13:30:30.382+01:00", dateTime.toString());
    }

    @Test
    public void testDateStringConverterWithAustralianEnglishAsSystemLocale() {
        Locale.setDefault(new Locale("en", "AU"));

        Annotation[] annotations = new Annotation[] {Annotated.class.getAnnotations()[0]};
        ParamConverter<OffsetDateTime> converter = this.createConverter(annotations);

        assertDoesNotThrow(() -> converter.fromString("Mon, 11 Jan 2021 15:30:05 EST"));
    }

    @Test
    public void testDateStringConverterWithNullInputReturnsNull() {
        ParamConverter<OffsetDateTime> converter = this.createConverter(null);
        assertNull(converter.fromString(null));
    }

    @Test
    public void testDateStringConverterWithEmptyInputReturnsNull() {
        ParamConverter<OffsetDateTime> converter = this.createConverter(null);
        assertNull(converter.fromString(""));
    }

    @Test
    public void testDateStringConverterWithInvalidDateThrowsException() {
        ParamConverter<OffsetDateTime> converter = this.createConverter(null);
        // invalid date (does not specify timezone code)
        assertThrows(RuntimeException.class,
            () -> converter.fromString("Mon, 11 Jan 2021 15:30:05"));
    }

    @Test
    public void testGetConverterWithNonOffsetDateTimeTypeReturnsNull() {
        OffsetDateTimeParamConverterProvider provider = new OffsetDateTimeParamConverterProvider();
        ParamConverter<Date> converter = provider
            .getConverter(Date.class, OffsetDateTime.class, null);
        assertNull(converter);
    }

    @Test
    public void testToStringWithNullInputReturnsNull() {
        ParamConverter<OffsetDateTime> converter = this.createConverter(null);
        assertNull(converter.toString(null));
    }

    @Test
    public void testToString() {
        ParamConverter<OffsetDateTime> converter = this.createConverter(null);
        OffsetDateTime dateTime = OffsetDateTime.now();
        assertEquals(dateTime.toString(), converter.toString(dateTime));
    }

    private ParamConverter<OffsetDateTime> createConverter(Annotation[] annotations) {
        OffsetDateTimeParamConverterProvider provider = new OffsetDateTimeParamConverterProvider();
        return provider.getConverter(OffsetDateTime.class, OffsetDateTime.class, annotations);
    }

    /*
     * Mock class with annotation that we need to test with
     */
    @Pattern(regexp = "EEE, dd MMM yyyy HH:mm:ss z")
    private static class Annotated {}
}
