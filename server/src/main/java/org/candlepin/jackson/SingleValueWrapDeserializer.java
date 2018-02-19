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

/**
 * The SingleValueWrapDeserializer handles the deserialization of single fields that are
 * wrapped in a JSON object/single-value-map, in the format of:
 * <pre> {@code "fieldName":{"fieldName":"value"} } </pre>
 * or: <pre> {@code "fieldName":"value" } </pre>
 *
 * Classes that extend this class should pass the name of the field they need to unwrap
 * as an argument to the super constructor.
 */
public abstract class SingleValueWrapDeserializer extends JsonDeserializer<String> {

    private static Logger log = LoggerFactory.getLogger(SingleValueWrapDeserializer.class);

    private String fieldName;

    public SingleValueWrapDeserializer(String fieldName) {
        this.fieldName = fieldName;
    }

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {

        TreeNode node = parser.readValueAsTree();

        if (node.isObject()) {
            log.debug("Processing {} as a containing object node.", this.fieldName);

            TreeNode valueNode = node.path(this.fieldName);
            if (valueNode.isMissingNode()) {
                throw new CandlepinJsonProcessingException(
                        "The field " + this.fieldName + " is missing from: " + node.asToken(),
                        parser.getCurrentLocation()
                );
            }

            return parseValueNode(valueNode);
        }
        else if (node.isValueNode()) {
            log.debug("Processing {} as a value node.", this.fieldName);

            return parseValueNode(node);
        }
        else {
            // Uh oh.
            throw new CandlepinJsonProcessingException(
                "Unexpected " + this.fieldName + " node type: " + node.asToken(),
                parser.getCurrentLocation()
            );
        }
    }

    private String parseValueNode(TreeNode valueNode) throws IOException {
        JsonParser subParser = valueNode.traverse();
        subParser.nextValue();
        String value = subParser.getValueAsString();
        subParser.close();

        log.debug("Found {} field's value", this.fieldName);
        return value;
    }
}
