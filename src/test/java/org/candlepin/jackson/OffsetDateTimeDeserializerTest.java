/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.jackson;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.core.JsonParser;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;


public class OffsetDateTimeDeserializerTest {

    private OffsetDateTimeDeserializer deserializer;

    private JsonParser parser;

    @BeforeEach
    public void setup() {
        deserializer = new OffsetDateTimeDeserializer();
        parser = mock(JsonParser.class);
    }

    @Test
    public void testDateTimeWithOffsetWithColon() throws IOException {
        when(parser.getText()).thenReturn("2021-01-24T13:30:30.382+01:00");
        OffsetDateTime dateTime = deserializer.deserialize(parser, null);
        assertEquals("2021-01-24T13:30:30.382+01:00", dateTime.toString());
    }

    @Test
    public void testDateTimeWithOffsetWithoutColon() throws IOException {
        when(parser.getText()).thenReturn("2021-01-24T13:30:30.382+0100");
        OffsetDateTime dateTime = deserializer.deserialize(parser, null);
        assertEquals("2021-01-24T13:30:30.382+01:00", dateTime.toString());
    }

    @Test
    public void testDateTimeWithOffsetZeroZ() throws IOException {
        when(parser.getText()).thenReturn("2021-01-24T13:30:30.382Z");
        OffsetDateTime dateTime = deserializer.deserialize(parser, null);
        assertEquals("2021-01-24T13:30:30.382Z", dateTime.toString());
    }

    @Test
    public void testDateTimeWithoutOffset() throws IOException {
        when(parser.getText()).thenReturn("2021-01-24T13:30:30.382");
        OffsetDateTime dateTime = deserializer.deserialize(parser, null);
        assertEquals("2021-01-24T13:30:30.382Z", dateTime.toString());
    }

    @Test
    public void testDateTimeWithoutOffsetAndMillis() throws IOException {
        when(parser.getText()).thenReturn("2021-01-24T13:30:30");
        OffsetDateTime dateTime = deserializer.deserialize(parser, null);
        assertEquals("2021-01-24T13:30:30Z", dateTime.toString());
    }

    @Test
    public void testDateTimeWithoutSeconds() throws IOException {
        when(parser.getText()).thenReturn("2021-01-24T13:30");
        OffsetDateTime dateTime = deserializer.deserialize(parser, null);
        assertEquals("2021-01-24T13:30Z", dateTime.toString());
    }

    @Test
    public void testDateTimeWithSpaceSeparatorForTime() throws IOException {
        when(parser.getText()).thenReturn("2021-01-24 13:30:30");
        OffsetDateTime dateTime = deserializer.deserialize(parser, null);
        assertEquals("2021-01-24T13:30:30Z", dateTime.toString());
    }

    @Test
    public void testDateTimeWithoutTime() throws IOException {
        when(parser.getText()).thenReturn("2021-01-24");
        OffsetDateTime dateTime = deserializer.deserialize(parser, null);
        assertEquals("2021-01-24T00:00Z", dateTime.toString());
    }

    @Test
    public void testDateTimeWithMalformedOffset() throws IOException {
        when(parser.getText()).thenReturn("2021-01-24T13:30:30.382Junk");
        assertThrows(DateTimeParseException.class, () -> deserializer.deserialize(parser, null));
    }

}
