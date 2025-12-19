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

import org.candlepin.dto.api.server.v1.GuestIdDTO;
import org.candlepin.exceptions.CandlepinJsonProcessingException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.ValueDeserializer;


/**
 * Handles the deserialization of the {@link GuestIdDTO} object by handling either a guest id string
 * or { "id":"value", "guestId":"value", "attributes":{"att":"val"} }.
 */
public class GuestIdDeserializer extends ValueDeserializer<GuestIdDTO> {
    private static final Logger log = LoggerFactory.getLogger(GuestIdDeserializer.class);

    private final ObjectMapper defaultMapper;

    /**
     * Creates a new GuestIdDeserializer using the specified mapper for deserializing
     * guest IDs received in object form. This mapper should NOT have GuestIdDeserializer
     * registered to avoid infinite recursion.
     *
     * @param defaultMapper
     *  mapper for processing guest Ids received in object form (without GuestIdDeserializer)
     */
    public GuestIdDeserializer(ObjectMapper defaultMapper) {
        if (defaultMapper == null) {
            throw new IllegalArgumentException("defaultMapper is null");
        }

        this.defaultMapper = defaultMapper;
    }

    @Override
    public GuestIdDTO deserialize(JsonParser parser, DeserializationContext context) {
        JsonNode node = context.readTree(parser);
        if (node.isTextual()) {
            log.debug("Processing node as a guest ID string: {}", node);

            return new GuestIdDTO()
                .guestId(node.textValue());
        }
        else if (node.isObject()) {
            log.debug("Processing node as a GuestIdDTO instance: {}", node);

            // Use the default mapper (without GuestIdDeserializer) to avoid recursion
            return defaultMapper.treeToValue(node, GuestIdDTO.class);
        }

        throw new CandlepinJsonProcessingException(
            "Unexpected guest ID value type: " + node.asToken(),
            parser.currentLocation()
        );
    }
}
