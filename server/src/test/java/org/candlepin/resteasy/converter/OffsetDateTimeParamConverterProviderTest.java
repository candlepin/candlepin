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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import java.lang.annotation.Annotation;
import java.time.OffsetDateTime;

import javax.validation.Payload;
import javax.validation.constraints.Pattern;
import javax.ws.rs.ext.ParamConverter;


public class OffsetDateTimeParamConverterProviderTest {

    @Test
    public void testNowValueParse() {
        OffsetDateTimeParamConverterProvider offsetDateTimeParamConverterProvider
            = new OffsetDateTimeParamConverterProvider();
        ParamConverter<OffsetDateTime> offsetDateTimeParamConverter = offsetDateTimeParamConverterProvider
            .getConverter(OffsetDateTime.class, OffsetDateTime.class, null);
        OffsetDateTime dateTime = offsetDateTimeParamConverter.fromString("now");
        assertNotNull(dateTime);
    }

    @Test
    public void testDateStringConverterWithPattern() {
        OffsetDateTimeParamConverterProvider offsetDateTimeParamConverterProvider
            = new OffsetDateTimeParamConverterProvider();
        Annotation[] annotations = new Annotation[1];
        annotations[0] = new Pattern() {

            @Override
            public String regexp() {
                return "EEE, dd MMM yyyy HH:mm:ss Z";
            }

            @Override
            public Flag[] flags() {
                return new Flag[0];
            }

            @Override
            public String message() {
                return null;
            }

            @Override
            public Class<?>[] groups() {
                return new Class[0];
            }

            @Override
            public Class<? extends Payload>[] payload() {
                return new Class[0];
            }

            @Override
            public Class<? extends Annotation> annotationType() {
                return Pattern.class;
            }
        };
        ParamConverter<OffsetDateTime> offsetDateTimeParamConverter = offsetDateTimeParamConverterProvider
            .getConverter(OffsetDateTime.class, OffsetDateTime.class, annotations);
        OffsetDateTime dateTime = offsetDateTimeParamConverter.fromString("Fri, 13 Mar 2020 13:30:30 +0530");
        assertEquals("2020-03-13T13:30:30+05:30", dateTime.toString());

    }

    @Test
    public void testDateStringConverterWithoutPattern() {
        OffsetDateTimeParamConverterProvider offsetDateTimeParamConverterProvider
            = new OffsetDateTimeParamConverterProvider();
        ParamConverter<OffsetDateTime> offsetDateTimeParamConverter = offsetDateTimeParamConverterProvider
            .getConverter(OffsetDateTime.class, OffsetDateTime.class, null);
        OffsetDateTime dateTime = offsetDateTimeParamConverter.fromString("2021-01-24T13:30:30.382+01:00");
        assertEquals("2021-01-24T13:30:30.382+01:00", dateTime.toString());
    }
}
