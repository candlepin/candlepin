/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.candlepin.common.exceptions.CandlepinJsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

/**
 * The ActivationKeyDTOProductDeserializer handles the deserialization of ActivationKeyDTO product sets,
 * reading them in the format of:
 * <pre> {@code [{"productId":"value1"}, ... ,{"productId":"valueN"}] } </pre>
 */
public class ActivationKeyDTOProductDeserializer extends JsonDeserializer<Set<String>> {

    private static Logger log = LoggerFactory.getLogger(ActivationKeyDTOProductDeserializer.class);

    private static final String PRODUCT_ID = "productId";

    @Override
    public Set<String> deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {

        Set<String> output = new HashSet<String>();
        TreeNode node = parser.readValueAsTree();
        if (node.isArray()) {
            log.debug("Processing products as an array of objects");

            int size = node.size();
            for (int i = 0; i < size; ++i) {
                TreeNode object = node.get(i);

                if (!object.isObject()) {
                    throw new CandlepinJsonProcessingException(
                            "Unexpected node type in array: " + object.asToken(),
                            parser.getCurrentLocation()
                    );
                }

                TreeNode fieldNode = object.get(PRODUCT_ID);

                if (fieldNode == null) {
                    throw new CandlepinJsonProcessingException(
                            "No attribute " + PRODUCT_ID + " defined in product object",
                            parser.getCurrentLocation()
                    );
                }

                if (!fieldNode.isValueNode()) {
                    throw new CandlepinJsonProcessingException(
                            "Unexpected value type for attribute " + PRODUCT_ID + ": " + fieldNode.asToken(),
                            parser.getCurrentLocation()
                    );
                }

                JsonParser subparser = fieldNode.traverse();
                subparser.nextValue();
                String field = subparser.getValueAsString();
                subparser.close();

                TreeNode valueNode = object.get(PRODUCT_ID);

                if (valueNode != null) {
                    if (!valueNode.isValueNode()) {
                        throw new CandlepinJsonProcessingException(
                                "Unexpected value type for " + PRODUCT_ID + " field value: " +
                                    valueNode.asToken(),
                                parser.getCurrentLocation()
                        );
                    }

                    subparser = valueNode.traverse();
                    subparser.nextValue();
                    String value = subparser.getValueAsString();
                    subparser.close();

                    if (value.equals("null")) {
                        IllegalArgumentException toThrow =
                            new IllegalArgumentException(PRODUCT_ID + " is null or empty");
                        log.error(PRODUCT_ID + " is null or empty", toThrow);
                        throw toThrow;
                    }

                    log.debug("Found a " + PRODUCT_ID + " field value", value);
                    output.add(value);
                }
                else {
                    IllegalArgumentException toThrow =
                        new IllegalArgumentException(PRODUCT_ID + " is null or empty");
                    log.error(PRODUCT_ID + " is null or empty", toThrow);
                    throw toThrow;
                }
            }
        }
        else {
            // Uh oh.
            throw new CandlepinJsonProcessingException(
                "Unexpected products node type: " + node.asToken(),
                parser.getCurrentLocation()
            );
        }
        return output;
    }
}
