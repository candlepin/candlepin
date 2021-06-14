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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.dto.api.v1.AttributeDTO;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;


class AttributeDeserializerTest {

    private static final String ARCH_KEY = "arch";
    private static final String ARCH_VALUE = "i386, x86_64";
    private static final String SOCKETS_KEY = "sockets";
    private static final String SOCKETS_VALUE = "4";
    private static final String RAM_KEY = "ram";
    private static final String RAM_VALUE = "16";

    private ObjectMapper mapper;
    private AttributeDeserializer deserializer;

    @BeforeEach
    public void setup() {
        mapper = new ObjectMapper();
        deserializer = new AttributeDeserializer();
    }

    @Test
    public void shouldDeserializeMapAttributes() throws IOException {
        String json = mapAttributes();
        List<AttributeDTO> attributes = deserialize(json);

        assertNotNull(attributes);
        assertTrue(containsName(attributes, ARCH_KEY));
        assertTrue(containsName(attributes, SOCKETS_KEY));
        assertTrue(containsName(attributes, RAM_KEY));
        assertEquals(findByName(attributes, ARCH_KEY).getValue(), ARCH_VALUE);
        assertEquals(findByName(attributes, SOCKETS_KEY).getValue(), SOCKETS_VALUE);
        assertEquals(findByName(attributes, RAM_KEY).getValue(), RAM_VALUE);
    }

    @Test
    public void shouldDeserializeListOfMapsAttributes() throws IOException {
        String json = listOfMapsAttributes();
        List<AttributeDTO> attributes = deserialize(json);

        assertNotNull(attributes);
        assertTrue(containsName(attributes, ARCH_KEY));
        assertTrue(containsName(attributes, SOCKETS_KEY));
        assertTrue(containsName(attributes, RAM_KEY));
        assertEquals(findByName(attributes, ARCH_KEY).getValue(), ARCH_VALUE);
        assertEquals(findByName(attributes, SOCKETS_KEY).getValue(), SOCKETS_VALUE);
        assertEquals(findByName(attributes, RAM_KEY).getValue(), RAM_VALUE);
    }

    @Test
    public void shouldDeserializeListOfNameValueAttributes() throws IOException {
        String json = listOfNameValueAttributes();
        List<AttributeDTO> attributes = deserialize(json);

        assertNotNull(attributes);
        assertTrue(containsName(attributes, ARCH_KEY));
        assertTrue(containsName(attributes, SOCKETS_KEY));
        assertTrue(containsName(attributes, RAM_KEY));
        assertEquals(findByName(attributes, ARCH_KEY).getValue(), ARCH_VALUE);
        assertEquals(findByName(attributes, SOCKETS_KEY).getValue(), SOCKETS_VALUE);
        assertEquals(findByName(attributes, RAM_KEY).getValue(), RAM_VALUE);
    }

    @Test
    public void shouldDeserializeListOfAttributesWithoutValue() throws IOException {
        String json = listOfAttributesWithoutValue();
        List<AttributeDTO> attributes = deserialize(json);

        assertNotNull(attributes);
        assertTrue(containsName(attributes, ARCH_KEY));
        assertTrue(containsName(attributes, SOCKETS_KEY));
        assertTrue(containsName(attributes, RAM_KEY));
        assertNull(findByName(attributes, ARCH_KEY).getValue());
        assertNull(findByName(attributes, SOCKETS_KEY).getValue());
        assertNull(findByName(attributes, RAM_KEY).getValue());
    }

    private List<AttributeDTO> deserialize(String json) throws IOException {
        InputStream stream = new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8));
        JsonParser parser = mapper.getFactory().createParser(stream);
        DeserializationContext ctxt = mapper.getDeserializationContext();
        return deserializer.deserialize(parser, ctxt);
    }

    private String mapAttributes() {
        return String.format("{\"%s\":\"%s\",\"%s\":\"%s\",\"%s\":\"%s\"}",
            ARCH_KEY, ARCH_VALUE,
            SOCKETS_KEY, SOCKETS_VALUE,
            RAM_KEY, RAM_VALUE);
    }

    private String listOfMapsAttributes() {
        return String.format("[{\"%s\":\"%s\"},{\"%s\":\"%s\"},{\"%s\":\"%s\"}]",
            ARCH_KEY, ARCH_VALUE,
            SOCKETS_KEY, SOCKETS_VALUE,
            RAM_KEY, RAM_VALUE);
    }

    private String listOfNameValueAttributes() {
        return String.format("[" +
                "{\"name\":\"%s\",\"value\":\"%s\"}," +
                "{\"name\":\"%s\",\"value\":\"%s\"}," +
                "{\"name\":\"%s\",\"value\":\"%s\"}]",
            ARCH_KEY, ARCH_VALUE,
            SOCKETS_KEY, SOCKETS_VALUE,
            RAM_KEY, RAM_VALUE);
    }

    private String listOfAttributesWithoutValue() {
        return String.format("[{\"name\":\"%s\"},{\"name\":\"%s\"},{\"name\":\"%s\"}]",
            ARCH_KEY, SOCKETS_KEY, RAM_KEY);
    }

    private boolean containsName(List<AttributeDTO> attributes, String name) {
        return attributes.stream()
            .anyMatch(attribute -> attribute.getName().equals(name));
    }

    private AttributeDTO findByName(List<AttributeDTO> attributes, String name) {
        return attributes.stream()
            .filter(attribute -> attribute.getName().equals(name))
            .findFirst()
            .orElse(null);
    }

}
