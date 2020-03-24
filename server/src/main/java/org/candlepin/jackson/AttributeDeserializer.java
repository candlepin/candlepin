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
import org.candlepin.dto.api.v1.AttributeDTO;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;


/**
 * The CandlepinAttributeDeserializer handles the deserialization of attributes, processing both
 * the current attribute mapping style and obsoleted formats.
 */
public class AttributeDeserializer extends StdDeserializer<List<AttributeDTO>> {
    private static Logger log = LoggerFactory.getLogger(AttributeDeserializer.class);

    public AttributeDeserializer() {
        this(null);
    }

    public AttributeDeserializer(Class<?> valueClass) {
        super(valueClass);
    }

    @Override
    public List<AttributeDTO> deserialize(JsonParser parser, DeserializationContext context)
        throws CandlepinJsonProcessingException {

        List<AttributeDTO> output = new ArrayList<>();
        TreeNode node = readTree(parser);

        if (node.isObject()) {
            log.debug("Processing attributes as a mapping of key/value pairs");
            parseMap(output, node);
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
                    parseMap(output, obj);
                    continue;
                }

                if (!fieldNode.isValueNode()) {
                    throw new CandlepinJsonProcessingException(
                        "Unexpected value type for attribute name: " + fieldNode.asToken(),
                        parser.getCurrentLocation()
                    );
                }

                String field = parseNode(fieldNode);

                TreeNode valueNode = obj.get("value");

                if (valueNode != null) {
                    String value = parseValue(valueNode);
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
                parser.getCurrentLocation()
            );
        }

        return output;
    }

    private TreeNode readTree(JsonParser parser) throws CandlepinJsonProcessingException {
        try {
            return parser.readValueAsTree();
        }
        catch (IOException e) {
            throw new CandlepinJsonProcessingException(
                "Unexpected error while parsing the root node.",
                parser.getCurrentLocation()
            );
        }
    }

    private void parseMap(List<AttributeDTO> output, TreeNode obj) throws CandlepinJsonProcessingException {
        Iterator<String> fieldNames = obj.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            String value = parseValue(obj.get(field));
            log.debug("Found key/value pair: {} = {}", field, value);
            output.add(createAttribute(field, value));
        }
    }

    private AttributeDTO createAttribute(String field, String value) {
        return new AttributeDTO().name(field).value(value);
    }

    private String parseValue(TreeNode node) throws CandlepinJsonProcessingException {
        if (!node.isValueNode()) {
            throw new CandlepinJsonProcessingException(
                "Unexpected value type for attribute value: " + node.asToken());
        }

        return parseNode(node);
    }

    private String parseNode(TreeNode node) throws CandlepinJsonProcessingException {
        JsonParser subparser = node.traverse();
        String value;
        try {
            subparser.nextValue();
            value = subparser.getValueAsString();
        }
        catch (IOException e) {
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
        catch (IOException e) {
            throw new CandlepinJsonProcessingException(
                "Unexpected error while closing the parser.");
        }
    }
}
