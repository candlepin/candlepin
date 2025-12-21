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

import org.candlepin.util.Util;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;



/**
 * A deserializer that turns ISO 8601 date strings into OffsetDateTime objects, with optional sections.
 * Specifically, it can handle parsing the following formats:
 * <ul>
 * <li>"2021-01-24T13:30:30.382+01:00"</li>
 * <li>"2021-01-24T13:30:30.382+0100"</li>
 * <li>"2021-01-24T13:30:30.382Z"</li>
 * <li>"2021-01-24T13:30:30.382"</li>
 * <li>"2021-01-24T13:30:30"</li>
 * <li>"2021-01-24 13:30:30"</li>
 * <li>"2021-01-24"</li>
 * </ul>
 *
 */
public class OffsetDateTimeDeserializer extends ValueDeserializer<OffsetDateTime> {

    private final DateTimeFormatter formatter;

    public OffsetDateTimeDeserializer() {
        this.formatter = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .append(DateTimeFormatter.ISO_LOCAL_DATE)
            .optionalStart().appendLiteral('T').optionalEnd()
            .optionalStart().appendLiteral(' ').optionalEnd()
            .appendOptional(DateTimeFormatter.ISO_LOCAL_TIME)
            .optionalStart().appendOffset("+HHMM", "+0000").optionalEnd()
            .optionalStart().appendOffset("+HH:MM", "+00:00").optionalEnd()
            .optionalStart().appendOffset("+HH", "+00").optionalEnd()
            .optionalStart().appendOffset("+Hmmss", "Z").optionalEnd()
            .optionalStart().appendOffsetId().optionalEnd()
            .toFormatter();
    }

    /**
     * Deserializer method that tries to parse an OffsetDateTime. If that is not possible, LocalDateTime
     * and LocalDate are attempted, and turned into OffsetDateTime with default missing values as start of
     * day (00:00) and UTC timezone (Z).
     *
     * {@inheritDoc}
     */
    @Override
    public OffsetDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) {

        return deserialize(jsonParser.getText());
    }

    public OffsetDateTime deserialize(String value) {
        return Util.parseOffsetDateTime(this.formatter, value);
    }
}
