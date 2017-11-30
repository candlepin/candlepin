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
import java.util.Iterator;

/**
 * The ActivationKeyDTOReleaseDeserializer handles the deserialization of ActivationKeyDTO
 * field ReleaseVersion, reading it in the formats of either:
 * <pre> {@code "releaseVer":{"releaseVer":"value"} } </pre>
 * or: <pre> {@code "releaseVer":"value" } </pre>
 */
public class ActivationKeyDTOReleaseDeserializer extends JsonDeserializer<String> {

    private static Logger log = LoggerFactory.getLogger(ActivationKeyDTOReleaseDeserializer.class);

    private static final String RELEASE_VERSION = "releaseVer";

    @Override
    public String deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {

        TreeNode node = parser.readValueAsTree();
        String fieldName = "";
        if (node.isObject()) {
            log.debug("Processing " + RELEASE_VERSION + " as an containing object node.");

            for (Iterator<String> fieldNames = node.fieldNames(); fieldNames.hasNext();) {
                fieldName = fieldNames.next();
                if (fieldName.equals(RELEASE_VERSION)) {
                    break;
                }
            }

            if (!fieldName.equals(RELEASE_VERSION)) {
                throw new CandlepinJsonProcessingException(
                        "Unexpected field name: '" + fieldName + "'. Expected '" + RELEASE_VERSION + "'.",
                        parser.getCurrentLocation()
                );
            }

            TreeNode valueNode = node.get(fieldName);
            if (!valueNode.isValueNode()) {
                throw new CandlepinJsonProcessingException(
                        "Unexpected value type in: " + valueNode.asToken(),
                        parser.getCurrentLocation()
                );
            }

            return parseValueNode(valueNode);
        }
        else if (node.isValueNode()) {
            log.debug("Processing " + RELEASE_VERSION + " as a value node.");

            return parseValueNode(node);
        }
        else {
            // Uh oh.
            throw new CandlepinJsonProcessingException(
                "Unexpected " + RELEASE_VERSION + " node type: " + node.asToken(),
                parser.getCurrentLocation()
            );
        }
    }

    private String parseValueNode(TreeNode valueNode) throws IOException {
        JsonParser subParser = valueNode.traverse();
        subParser.nextValue();
        String value = subParser.getValueAsString();
        subParser.close();

        log.debug("Found " + RELEASE_VERSION + " field's value", value);
        return value;
    }
}
