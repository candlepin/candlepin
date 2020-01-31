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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringWriter;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.*;


/**
 * Basic test for OffsetDateTimeSerializer
 */
public class OffsetDateTimeSerializerTest {

    @Test
    public void testOffsetDateTimeSerializer() throws IOException {
        OffsetDateTimeSerializer serializer = new OffsetDateTimeSerializer();
        StringWriter stringWriter = new StringWriter();
        JsonGenerator jsonGenerator = new JsonFactory().createGenerator(stringWriter);

        OffsetDateTime dateTime = OffsetDateTime.parse("2021-01-24T13:30:30.382+01:00");
        serializer.serialize(dateTime, jsonGenerator, null);
        jsonGenerator.flush();
        assertEquals("\"2021-01-24T13:30:30+0100\"", stringWriter.toString());
    }
}
