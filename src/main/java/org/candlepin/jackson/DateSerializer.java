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

import tools.jackson.core.JsonGenerator;
import tools.jackson.databind.SerializationContext;
import tools.jackson.databind.ValueSerializer;

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
public class DateSerializer extends ValueSerializer<Date> {
    private final DateTimeFormatter dateFormat;

    public DateSerializer() {
        // This DateTimeFormatter pattern is ISO 8601 without milliseconds
        this.dateFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");
    }

    @Override
    public void serialize(Date date, JsonGenerator jgen, SerializationContext serializerProvider) {
        Instant instant = date.toInstant();
        LocalDateTime localDateTime = LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
        jgen.writeString(this.dateFormat.format(ZonedDateTime.of(localDateTime, ZoneId.of("UTC"))));
    }
}
