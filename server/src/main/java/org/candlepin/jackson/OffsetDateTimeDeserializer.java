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
package org.candlepin.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.TemporalAccessor;

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
public class OffsetDateTimeDeserializer extends JsonDeserializer<OffsetDateTime> {

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
              .optionalStart().appendZoneId().optionalEnd()
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
    public OffsetDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext)
        throws IOException {

        TemporalAccessor temporalAccessor = formatter.parseBest(jsonParser.getText(),
            OffsetDateTime::from,
            LocalDateTime::from,
            LocalDate::from);
        if (temporalAccessor instanceof OffsetDateTime) {
            return OffsetDateTime.from(temporalAccessor);
        }
        else if (temporalAccessor instanceof LocalDateTime) {
            return LocalDateTime.from(temporalAccessor).atOffset(ZoneOffset.UTC);
        }
        else {
            return LocalDate.from(temporalAccessor).atStartOfDay().atOffset(ZoneOffset.UTC);
        }
    }

}
