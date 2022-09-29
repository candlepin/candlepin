/**
 * Copyright (c) 2009 - 2022 Red Hat, Inc.
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

import org.candlepin.dto.api.server.v1.GuestIdDTO;
import org.candlepin.exceptions.CandlepinJsonProcessingException;
import org.candlepin.util.ObjectMapperFactory;

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Handles the deserialization of the {@link GuestIdDTO} object by handling either a guest id string
 * or { "id":"value", "guestId":"value", "attributes":{"att":"val"} }.
 */
public class GuestIdDeserializer extends JsonDeserializer<GuestIdDTO> {
    private static Logger log = LoggerFactory.getLogger(GuestIdDeserializer.class);
    private static ObjectMapper mapper = ObjectMapperFactory.getObjectMapper();

    @Override
    public GuestIdDTO deserialize(JsonParser parser, DeserializationContext context)
        throws IOException, JacksonException {
        TreeNode rootNode = parser.readValueAsTree();
        if (rootNode.isValueNode()) {
            return parseValueNode(rootNode, parser);
        }
        else if (rootNode.isObject()) {
            log.debug("Processing GuestIdDTO as a containing object node.");
            return mapper.convertValue(rootNode, GuestIdDTO.class);
        }
        else {
            throw new CandlepinJsonProcessingException(
                "Unexpected guest id value type: " + rootNode.asToken(),
                parser.getCurrentLocation()
            );
        }
    }

    private GuestIdDTO parseValueNode(TreeNode node, JsonParser parser) throws IOException {
        log.debug("Processing GuestIdDTO as a value node.");
        JsonParser subParser = node.traverse();
        subParser.nextValue();
        String value = subParser.getValueAsString();
        subParser.close();

        GuestIdDTO guest = new GuestIdDTO();
        guest.setGuestId(value);
        return guest;
    }
}
