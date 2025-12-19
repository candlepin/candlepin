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

import java.util.Map;


/**
 * The CandlepinLegacyAttributeSerializer handles the serialization of attribute maps, writing them
 * in the legacy attribute format of [{"key1":"value1"}, ... ,{"keyN":"valueN"}].
 */
public class CandlepinLegacyAttributeSerializer extends ValueSerializer<Map<String, String>> {

    @Override
    public void serialize(Map<String, String> map, JsonGenerator generator, SerializationContext provider) {

        generator.writeStartArray();

        if (map != null && !map.isEmpty()) {
            for (Map.Entry<String, String> entry : map.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();

                if (key != null && !key.isEmpty()) {
                    generator.writeStartObject();
                    generator.writeStringProperty("name", key);
                    generator.writeStringProperty("value", value);
                    generator.writeEndObject();
                }
            }
        }

        generator.writeEndArray();
    }
}
