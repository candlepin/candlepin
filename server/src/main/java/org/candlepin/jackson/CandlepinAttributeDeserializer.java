/**
 * Copyright (c) 2009 - 2016 Red Hat, Inc.
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

import org.candlepin.common.exceptions.CandlepinJsonProcessingException;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;



/**
 * The CandlepinAttributeDeserializer handles the deserialization of attributes, processing both
 * the current attribute mapping style and obsoleted formats.
 */
//@Component
public class CandlepinAttributeDeserializer extends StdDeserializer<Map<String, String>> {
    private static Logger log = LoggerFactory.getLogger(CandlepinAttributeDeserializer.class);

    public CandlepinAttributeDeserializer() {
        this(null);
    }

    public CandlepinAttributeDeserializer(Class<?> valueClass) {
        super(valueClass);
    }

    @Override
    public Map<String, String> deserialize(JsonParser parser, DeserializationContext context)
        throws IOException, JsonProcessingException {

        Map<String, String> output = new HashMap<>();
        TreeNode node = parser.readValueAsTree();

        if (node.isObject()) {
            log.debug("Processing attributes as a mapping of key/value pairs");

            // This is what we want, key/value pairs (hopefully).
            for (Iterator<String> fieldNames = node.fieldNames(); fieldNames.hasNext();) {
                String field = fieldNames.next();
                TreeNode valueNode = node.get(field);

                if (!valueNode.isValueNode()) {
                    throw new CandlepinJsonProcessingException(
                        "Unexpected value type in map: " + valueNode.asToken(),
                        parser.getCurrentLocation()
                    );
                }

                JsonParser subparser = valueNode.traverse();
                subparser.nextValue();
                String value = subparser.getValueAsString();
                subparser.close();

                log.debug("Found key/value pair: {} = {}", field, value);
                output.put(field, value);
            }
        }
        else if (node.isArray()) {
            log.debug("Processing attributes as an array of attribute objects");
            // Probably old collection of objects containing name/value attribute.
            // Iterate over the objects, adding the values to the map.

            int size = node.size();
            for (int i = 0; i < size; ++i) {
                TreeNode obj = node.get(i);

                if (!obj.isObject()) {
                    throw new CandlepinJsonProcessingException(
                        "Unexpected value type in array: " + obj.asToken(),
                        parser.getCurrentLocation()
                    );
                }

                TreeNode fieldNode = obj.get("name");

                if (fieldNode == null) {
                    throw new CandlepinJsonProcessingException(
                        "No attribute name defined in attribute object",
                        parser.getCurrentLocation()
                    );
                }

                if (!fieldNode.isValueNode()) {
                    throw new CandlepinJsonProcessingException(
                        "Unexpected value type for attribute name: " + fieldNode.asToken(),
                        parser.getCurrentLocation()
                    );
                }

                JsonParser subparser = fieldNode.traverse();
                subparser.nextValue();
                String field = subparser.getValueAsString();
                subparser.close();

                TreeNode valueNode = obj.get("value");

                if (valueNode != null) {
                    if (!valueNode.isValueNode()) {
                        throw new CandlepinJsonProcessingException(
                            "Unexpected value type for attribute value: " + valueNode.asToken(),
                            parser.getCurrentLocation()
                        );
                    }

                    subparser = valueNode.traverse();
                    subparser.nextValue();
                    String value = subparser.getValueAsString();
                    subparser.close();

                    log.debug("Found key/value pair: {} = {}", field, value);
                    output.put(field, value);
                }
                else {
                    log.debug("Found key/value pair: {} = {}", field, null);
                    output.put(field, null);
                }
            }
        }
        else {
            log.debug("Processing attributes as an array of attribute objects");

            // Uh oh.
            throw new CandlepinJsonProcessingException(
                "Unexpected attribute value type: " + node.asToken(),
                parser.getCurrentLocation()
            );
        }

        return output;
    }
}
