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
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.TimeZone;
/**
 * DateSerializer
 *
 * This serializer removes milliseconds from Date objects in order to be more compatible with MySql.
 * The format used is ISO 8601
 */
public class DateSerializer extends JsonSerializer<Date> {
    // This SimpleDateFormat is iso 8601 without milliseconds
    public static final SimpleDateFormat ISO_8601_WITHOUT_MILLISECONDS;
    static {
        ISO_8601_WITHOUT_MILLISECONDS = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
        ISO_8601_WITHOUT_MILLISECONDS.setTimeZone(TimeZone.getTimeZone("UTC"));
    }

    @Override
    public void serialize(Date date, JsonGenerator jgen, SerializerProvider serializerProvider)
            throws IOException, JsonProcessingException {
        jgen.writeString(ISO_8601_WITHOUT_MILLISECONDS.format(date));
    }
}
