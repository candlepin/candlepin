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

import org.candlepin.exceptions.CandlepinJsonProcessingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JsonParser;
import tools.jackson.core.TreeNode;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;


/**
 * The CandlepinAttributeDeserializer handles the deserialization of attributes, processing both
 * the current attribute mapping style and obsoleted formats.
 */
public class CandlepinAttributeDeserializer extends StdDeserializer<Map<String, String>> {
    private static Logger log = LoggerFactory.getLogger(CandlepinAttributeDeserializer.class);

    public CandlepinAttributeDeserializer() {
        super(Map.class);
    }

    @Override
    public Map<String, String> deserialize(JsonParser parser, DeserializationContext context) {

        Map<String, String> output = new HashMap<>();
        TreeNode node = parser.readValueAsTree();

        if (node.isObject()) {
            log.debug("Processing attributes as a mapping of key/value pairs");

            // This is what we want, key/value pairs (hopefully).
            for (Iterator<String> fieldNames = node.propertyNames().iterator(); fieldNames.hasNext();) {
                String field = fieldNames.next();
                TreeNode valueNode = node.get(field);

                if (!valueNode.isValueNode()) {
                    throw new CandlepinJsonProcessingException(
                        "Unexpected value type in map: " + valueNode.asToken(),
                        parser.currentLocation()
                    );
                }

                JsonParser subparser = valueNode.traverse(context);
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
                        parser.currentLocation()
                    );
                }

                TreeNode fieldNode = obj.get("name");

                if (fieldNode == null) {
                    throw new CandlepinJsonProcessingException(
                        "No attribute name defined in attribute object",
                        parser.currentLocation()
                    );
                }

                if (!fieldNode.isValueNode()) {
                    throw new CandlepinJsonProcessingException(
                        "Unexpected value type for attribute name: " + fieldNode.asToken(),
                        parser.currentLocation()
                    );
                }

                JsonParser subparser = fieldNode.traverse(context);
                subparser.nextValue();
                String field = subparser.getValueAsString();
                subparser.close();

                TreeNode valueNode = obj.get("value");

                if (valueNode != null) {
                    if (!valueNode.isValueNode()) {
                        throw new CandlepinJsonProcessingException(
                            "Unexpected value type for attribute value: " + valueNode.asToken(),
                            parser.currentLocation()
                        );
                    }

                    subparser = valueNode.traverse(context);
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
                parser.currentLocation()
            );
        }

        return output;
    }
}
