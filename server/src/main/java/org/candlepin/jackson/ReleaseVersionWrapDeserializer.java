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

import org.candlepin.common.exceptions.CandlepinJsonProcessingException;
import org.candlepin.dto.api.v1.ReleaseVerDTO;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


/**
 * Handles the deserialization of the "releaseVer" field by wrapping it in a {@link ReleaseVerDTO} object,
 * by handling both of the following formats: <pre> {@code "releaseVer":"value" } </pre> and
 * <pre> {@code "releaseVer":{"releaseVer ":"value"} } </pre>.
 */
public class ReleaseVersionWrapDeserializer extends JsonDeserializer<ReleaseVerDTO> {

    private static Logger log = LoggerFactory.getLogger(ReleaseVersionWrapDeserializer.class);

    private static String fieldName = "releaseVer";

    @Override
    public ReleaseVerDTO deserialize(JsonParser parser, DeserializationContext context)
        throws IOException {

        TreeNode node = parser.readValueAsTree();

        if (node.isValueNode()) {
            log.debug("Processing {} as a value node.", fieldName);

            return parseValueNode(node);
        }
        else if (node.isObject()) {
            log.debug("Processing {} as a containing object node.", fieldName);

            TreeNode valueNode = node.path(fieldName);
            if (valueNode.isMissingNode()) {
                throw new CandlepinJsonProcessingException(
                    "The field " + fieldName + " is missing from: " + node.asToken(),
                    parser.getCurrentLocation()
                );
            }

            return parseValueNode(valueNode);
        }
        else {
            // Uh oh.
            throw new CandlepinJsonProcessingException(
                "Unexpected " + fieldName + " node type: " + node.asToken(),
                parser.getCurrentLocation()
            );
        }
    }

    private ReleaseVerDTO parseValueNode(TreeNode valueNode) throws IOException {
        JsonParser subParser = valueNode.traverse();
        subParser.nextValue();
        String value = subParser.getValueAsString();
        subParser.close();

        log.debug("Found {} field's value", fieldName);
        return new ReleaseVerDTO().releaseVer(value);
    }
}
