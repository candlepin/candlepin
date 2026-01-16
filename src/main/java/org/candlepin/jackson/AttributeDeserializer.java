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

import org.candlepin.dto.api.server.v1.AttributeDTO;
import org.candlepin.exceptions.CandlepinJsonProcessingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JacksonException;
import tools.jackson.core.JsonParser;
import tools.jackson.core.TreeNode;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.deser.std.StdDeserializer;

import java.util.ArrayList;
import java.util.List;


/**
 * The CandlepinAttributeDeserializer handles the deserialization of attributes, processing both
 * the current attribute mapping style and obsoleted formats.
 */
public class AttributeDeserializer extends StdDeserializer<List<AttributeDTO>> {
    private static Logger log = LoggerFactory.getLogger(AttributeDeserializer.class);

    public AttributeDeserializer() {
        super(List.class);
    }

    @Override
    public List<AttributeDTO> deserialize(JsonParser parser, DeserializationContext context)
        throws CandlepinJsonProcessingException {

        List<AttributeDTO> output = new ArrayList<>();
        TreeNode node = readTree(parser);

        if (node.isObject()) {
            log.debug("Processing attributes as a mapping of key/value pairs");
            parseMap(output, node, context);
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
                    parseMap(output, obj, context);
                    continue;
                }

                if (!fieldNode.isValueNode()) {
                    throw new CandlepinJsonProcessingException(
                        "Unexpected value type for attribute name: " + fieldNode.asToken(),
                        parser.currentLocation()
                    );
                }

                String field = parseNode(fieldNode, context);

                TreeNode valueNode = obj.get("value");

                if (valueNode != null) {
                    String value = parseValue(valueNode, context);
                    log.debug("Found key/value pair: {} = {}", field, value);
                    output.add(createAttribute(field, value));
                }
                else {
                    log.debug("Found key/value pair: {} = {}", field, null);
                    output.add(createAttribute(field, null));
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

    private TreeNode readTree(JsonParser parser) throws CandlepinJsonProcessingException {
        try {
            return parser.readValueAsTree();
        }
        catch (JacksonException e) {
            throw new CandlepinJsonProcessingException(
                "Unexpected error while parsing the root node: " + e.getMessage(),
                parser.currentLocation()
            );
        }
    }

    private void parseMap(List<AttributeDTO> output, TreeNode obj, DeserializationContext context)
        throws CandlepinJsonProcessingException {
        obj.propertyNames().stream().forEach(field -> {
            String value = parseValue(obj.get(field), context);
            log.debug("Found key/value pair: {} = {}", field, value);
            output.add(createAttribute(field, value));
        });
    }

    private AttributeDTO createAttribute(String field, String value) {
        return new AttributeDTO().name(field).value(value);
    }

    private String parseValue(TreeNode node, DeserializationContext context)
        throws CandlepinJsonProcessingException {
        if (!node.isValueNode()) {
            throw new CandlepinJsonProcessingException(
                "Unexpected value type for attribute value: " + node.asToken());
        }

        return parseNode(node, context);
    }

    private String parseNode(TreeNode node, DeserializationContext context)
        throws CandlepinJsonProcessingException {
        JsonParser subparser = node.traverse(context);
        String value;
        try {
            subparser.nextValue();
            value = subparser.getValueAsString();
        }
        catch (JacksonException e) {
            throw new CandlepinJsonProcessingException(
                "Unexpected eeror while parsing the value of: " + node.asToken());
        }
        finally {
            close(subparser);
        }

        return value;
    }

    private void close(JsonParser parser) throws CandlepinJsonProcessingException {
        try {
            parser.close();
        }
        catch (JacksonException e) {
            throw new CandlepinJsonProcessingException(
                "Unexpected error while closing the parser.");
        }
    }
}
