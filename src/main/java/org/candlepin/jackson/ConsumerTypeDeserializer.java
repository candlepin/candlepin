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

import org.candlepin.dto.api.server.v1.ConsumerTypeDTO;
import org.candlepin.exceptions.CandlepinJsonProcessingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JsonParser;
import tools.jackson.core.TreeNode;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;


/**
 * Handles the deserialization of the "label" field by wrapping it in a {@link ConsumerTypeDTO} object,
 * by handling both of the following formats: <pre> {@code "type":"value" } </pre> and
 * <pre> {@code "type":{"label ":"value", "manifest":"value"} } </pre>.
 */
public class ConsumerTypeDeserializer extends ValueDeserializer<ConsumerTypeDTO> {

    private static Logger log = LoggerFactory.getLogger(ConsumerTypeDeserializer.class);

    private static String fieldName = "label";

    @Override
    public ConsumerTypeDTO deserialize(JsonParser parser, DeserializationContext context) {

        TreeNode node = parser.readValueAsTree();

        if (node.isValueNode()) {
            log.debug("Processing {} as a value node.", fieldName);

            return parseValueNode(node, context);
        }
        else if (node.isObject()) {
            log.debug("Processing {} as a containing object node.", fieldName);

            TreeNode valueNode = node.path(fieldName);
            if (valueNode.isMissingNode()) {
                throw new CandlepinJsonProcessingException("Unexpected consumer type format: " +
                    node.asToken(), parser.currentLocation());
            }

            return parseValueNode(valueNode, context);
        }
        else {
            // Uh oh.
            throw new CandlepinJsonProcessingException("Unexpected consumer type format: " +
                node.asToken(), parser.currentLocation());
        }
    }

    private ConsumerTypeDTO parseValueNode(TreeNode valueNode, DeserializationContext context) {
        JsonParser subParser = valueNode.traverse(context);
        subParser.nextValue();
        String value = subParser.getValueAsString();
        subParser.close();

        log.debug("Found {} field's value", fieldName);
        return new ConsumerTypeDTO()
            .label(value);
    }
}
