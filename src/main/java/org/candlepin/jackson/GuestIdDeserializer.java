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

import com.fasterxml.jackson.core.JacksonException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectReader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

/**
 * Handles the deserialization of the {@link GuestIdDTO} object by handling either a guest id string
 * or { "id":"value", "guestId":"value", "attributes":{"att":"val"} }.
 */
public class GuestIdDeserializer extends JsonDeserializer<GuestIdDTO> {
    private static final Logger log = LoggerFactory.getLogger(GuestIdDeserializer.class);

    private ObjectReader reader;

    /**
     * Creates a new GuestIdDeserializer using the specified reader as the
     * default/base reader for processing guest IDs received in object form.
     *
     * @param reader
     *  base reader for processing guest Ids recieved in object form
     */
    public GuestIdDeserializer(ObjectReader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("reader is null");
        }

        this.reader = reader;
    }

    @Override
    public GuestIdDTO deserialize(JsonParser parser, DeserializationContext context)
        throws IOException, JacksonException {
        JsonNode node = context.readTree(parser);
        if (node.isTextual()) {
            log.debug("Processing node as a guest ID string: {}", node);

            return new GuestIdDTO()
                .guestId(node.textValue());
        }
        else if (node.isObject()) {
            log.debug("Processing node as a GuestIdDTO instance: {}", node);

            return reader.readValue(node, GuestIdDTO.class);
        }

        throw new CandlepinJsonProcessingException(
            "Unexpected guest ID value type: " + node.asToken(),
            parser.getCurrentLocation()
        );
    }
}
