/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.dto.api.server.v1.ConsumerTypeDTO;
import org.candlepin.exceptions.CandlepinJsonProcessingException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.io.IOException;


class ConsumerTypeDeserializerTest {

    private static final String TYPE_LABEL = "system";

    private ObjectMapper mapper;
    private ConsumerTypeDeserializer deserializer;

    @BeforeEach
    public void setup() {
        this.deserializer = new ConsumerTypeDeserializer();

        SimpleModule module = new SimpleModule();
        module.addDeserializer(ConsumerTypeDTO.class, deserializer);

        this.mapper = JsonMapper.builder()
            .addModule(module)
            .build();
    }

    @Test
    public void shouldDeserializeSimpleStringValue() throws IOException {
        String json = "\"" + TYPE_LABEL + "\"";
        ConsumerTypeDTO consumerType = deserialize(json);

        assertNotNull(consumerType);
        assertEquals(TYPE_LABEL, consumerType.getLabel());
    }

    @Test
    public void shouldDeserializeObjectWithLabelField() throws IOException {
        String json = "{\"label\":\"" + TYPE_LABEL + "\"}";
        ConsumerTypeDTO consumerType = deserialize(json);

        assertNotNull(consumerType);
        assertEquals(TYPE_LABEL, consumerType.getLabel());
    }

    @Test
    public void shouldThrowExceptionWhenObjectMissingLabelField() {
        String json = "{\"id\":\"test-id\"}";
        CandlepinJsonProcessingException exception = assertThrows(
            CandlepinJsonProcessingException.class,
            () -> deserialize(json));
        assertTrue(exception.getMessage().startsWith("Unexpected consumer type format:"));
    }

    @Test
    public void shouldThrowExceptionWhenValueIsArray() {
        String json = "[\"value1\",\"value2\"]";
        CandlepinJsonProcessingException exception = assertThrows(
            CandlepinJsonProcessingException.class,
            () -> deserialize(json));
        assertTrue(exception.getMessage().startsWith("Unexpected consumer type format:"));
    }

    private ConsumerTypeDTO deserialize(String json) {
        return mapper.readValue(json, ConsumerTypeDTO.class);
    }
}
