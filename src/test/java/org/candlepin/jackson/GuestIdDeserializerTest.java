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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.dto.api.server.v1.GuestIdDTO;
import org.candlepin.exceptions.CandlepinJsonProcessingException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.module.SimpleModule;

import java.io.IOException;


class GuestIdDeserializerTest {

    private ObjectMapper mapper;
    private ObjectMapper defaultMapper;
    private GuestIdDeserializer deserializer;

    @BeforeEach
    public void setup() {
        // Create a default mapper WITHOUT the GuestIdDeserializer to avoid recursion
        this.defaultMapper = JsonMapper.builder().build();
        this.deserializer = new GuestIdDeserializer(defaultMapper);

        SimpleModule module = new SimpleModule();
        module.addDeserializer(GuestIdDTO.class, deserializer);

        this.mapper = JsonMapper.builder()
            .addModule(module)
            .build();
    }

    @Test
    public void shouldThrowExceptionWhenDefaultMapperIsNull() {
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> new GuestIdDeserializer(null));
        assertEquals("defaultMapper is null", exception.getMessage());
    }

    @Test
    public void shouldDeserializeSimpleStringValue() throws IOException {
        String json = "\"guest-123-abc\"";
        GuestIdDTO guestId = deserialize(json);

        assertNotNull(guestId);
        assertEquals("guest-123-abc", guestId.getGuestId());
    }

    @Test
    public void shouldDeserializeObjectWithGuestIdField() throws IOException {
        String json = "{\"guestId\":\"guest-123-abc\"}";
        GuestIdDTO guestId = deserialize(json);

        assertNotNull(guestId);
        assertEquals("guest-123-abc", guestId.getGuestId());
    }

    @Test
    public void shouldDeserializeObjectWithAllFields() throws IOException {
        String json = "{\"id\":\"db-id-456\",\"guestId\":\"guest-123-abc\"," +
            "\"attributes\":{\"virtWhoType\":\"libvirt\",\"active\":\"1\"}}";
        GuestIdDTO guestId = deserialize(json);

        assertNotNull(guestId);
        assertEquals("db-id-456", guestId.getId());
        assertEquals("guest-123-abc", guestId.getGuestId());
        assertNotNull(guestId.getAttributes());
        assertEquals(2, guestId.getAttributes().size());
        assertEquals("libvirt", guestId.getAttributes().get("virtWhoType"));
        assertEquals("1", guestId.getAttributes().get("active"));
    }

    @Test
    public void shouldDeserializeObjectWithEmptyAttributes() throws IOException {
        String json = "{\"guestId\":\"guest-123-abc\",\"attributes\":{}}";
        GuestIdDTO guestId = deserialize(json);

        assertNotNull(guestId);
        assertEquals("guest-123-abc", guestId.getGuestId());
        assertNotNull(guestId.getAttributes());
        assertTrue(guestId.getAttributes().isEmpty());
    }

    @Test
    public void shouldDeserializeObjectWithNullGuestId() throws IOException {
        String json = "{\"id\":\"db-id-789\",\"guestId\":null}";
        GuestIdDTO guestId = deserialize(json);

        assertNotNull(guestId);
        assertEquals("db-id-789", guestId.getId());
        assertNull(guestId.getGuestId());
    }

    @Test
    public void shouldThrowExceptionWhenValueIsArray() {
        String json = "[\"guest1\",\"guest2\"]";
        CandlepinJsonProcessingException exception = assertThrows(
            CandlepinJsonProcessingException.class,
            () -> deserialize(json));
        assertTrue(exception.getMessage().startsWith("Unexpected guest ID value type:"));
    }

    @Test
    public void shouldThrowExceptionWhenValueIsNumber() {
        String json = "12345";
        CandlepinJsonProcessingException exception = assertThrows(
            CandlepinJsonProcessingException.class,
            () -> deserialize(json));
        assertTrue(exception.getMessage().startsWith("Unexpected guest ID value type:"));
    }

    @Test
    public void shouldThrowExceptionWhenValueIsBoolean() {
        String json = "true";
        CandlepinJsonProcessingException exception = assertThrows(
            CandlepinJsonProcessingException.class,
            () -> deserialize(json));
        assertTrue(exception.getMessage().startsWith("Unexpected guest ID value type:"));
    }

    private GuestIdDTO deserialize(String json) {
        return mapper.readValue(json, GuestIdDTO.class);
    }
}
