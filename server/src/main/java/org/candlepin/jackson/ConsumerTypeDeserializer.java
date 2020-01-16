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
import org.candlepin.dto.api.v1.ConsumerTypeDTO;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;



/**
 * The ConsumerTypeDeserializer handles deserialization of consumer types defined as strings or
 * objects on existing objects.
 */
public class ConsumerTypeDeserializer extends StdDeserializer<ConsumerTypeDTO> {
    private static Logger log = LoggerFactory.getLogger(ConsumerTypeDeserializer.class);

    public ConsumerTypeDeserializer() {
        this(null);
    }

    public ConsumerTypeDeserializer(Class<?> valueClass) {
        super(valueClass);
    }

    @Override
    public ConsumerTypeDTO deserialize(JsonParser parser, DeserializationContext context)
        throws IOException, JsonProcessingException {

        // We're expecting an object or a string
        switch (parser.currentToken()) {
            case START_OBJECT:
                return this.parseTypeFromObject(parser);

            case VALUE_STRING:
                return this.parseTypeFromString(parser);

            default:
                throw new CandlepinJsonProcessingException("Unexpected consumer type format: " +
                    parser.readValueAsTree(), parser.getCurrentLocation());
        }
    }

    private ConsumerTypeDTO parseTypeFromObject(JsonParser parser) throws IOException {
        ObjectMapper mapper = (ObjectMapper) parser.getCodec();
        return mapper.readValue(parser, ConsumerTypeDTO.class);
    }

    private ConsumerTypeDTO parseTypeFromString(JsonParser parser) throws IOException {
        String label = parser.getText();

        // Try to determine the manifest flag from ConsumerTypeEnum, if it's present there.
        // If not, assume false.

        try {
            ConsumerTypeEnum cte = ConsumerTypeEnum.valueOf(label.toUpperCase());

            return new ConsumerTypeDTO()
                .label(label)
                .manifest(cte.isManifest());
        }
        catch (Exception e) {
            // Label was null or did not represent a known consumer type

            return new ConsumerTypeDTO()
                .label(label);
        }
    }
}
