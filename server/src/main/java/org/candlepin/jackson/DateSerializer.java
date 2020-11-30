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
package org.candlepin.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;

/**
 * DateSerializer
 * This serializer removes milliseconds from Date objects in order to be more
 * compatible with MySql.
 * The format used is ISO 8601
 */
//@Component
public class DateSerializer extends JsonSerializer<Date> {
    private final DateTimeFormatter dateFormat;

    public DateSerializer() {
        // This DateTimeFormatter pattern is ISO 8601 without milliseconds
        this.dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    }

    @Override
    public void serialize(Date date, JsonGenerator jgen, SerializerProvider serializerProvider)
        throws IOException {
        Instant instant = date.toInstant();
        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        jgen.writeString(this.dateFormat.format(ZonedDateTime.of(localDateTime, ZoneId.of("UTC"))));
    }
}
