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

package org.candlepin.dto.manifest.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.util.ObjectMapperFactory;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Date;

public class MetaDTOTest {

    @Test
    public void testMutableFields() {
        Date created = new Date();
        MetaDTO dto = new MetaDTO()
            .setVersion("5.0.3-1")
            .setCreated(created)
            .setPrincipalName("admin")
            .setWebAppPrefix("candlepin")
            .setCdnLabel("cdn-label");

        assertEquals("2.0.0", dto.getManifestVersion());
        assertEquals("5.0.3-1", dto.getVersion());
        assertEquals(created, dto.getCreated());
        assertEquals("admin", dto.getPrincipalName());
        assertEquals("candlepin", dto.getWebAppPrefix());
        assertEquals("cdn-label", dto.getCdnLabel());
    }

    @Test
    public void testEmptyDto() {
        MetaDTO dto = new MetaDTO();

        assertEquals("2.0.0", dto.getManifestVersion());
        assertNull(dto.getVersion());
        assertNull(dto.getCreated());
        assertNull(dto.getPrincipalName());
        assertNull(dto.getWebAppPrefix());
        assertNull(dto.getCdnLabel());
    }
    @Test
    public void testSerializedMetadataContainsBothVersions() {
        ObjectMapper mapper = ObjectMapperFactory.getObjectMapper();
        JsonNode json = mapper.valueToTree(new MetaDTO().setVersion("5.0.3-1"));

        assertEquals("5.0.3-1", json.get("version").asString());
        assertEquals("2.0.0", json.get("manifestVersion").asString());
    }

    @Test
    public void testDeserializationCannotOverrideManifestVersion() {
        ObjectMapper mapper = ObjectMapperFactory.getObjectMapper();
        MetaDTO dto = mapper.readValue(
            "{\"version\":\"5.0.3-1\",\"manifestVersion\":\"1.0.0\",\"principalName\":\"admin\"}", MetaDTO.class);

        assertEquals("2.0.0", dto.getManifestVersion());
        assertEquals("admin", dto.getPrincipalName());
        assertEquals("5.0.3-1", dto.getVersion());
        assertEquals("5.0.3-1", new MetaDTO(dto).getVersion());
        assertEquals("5.0.3-1", dto.clone().getVersion());
        assertEquals("2.0.0", new MetaDTO(dto).getManifestVersion());
        assertEquals("2.0.0", dto.clone().getManifestVersion());
    }
    @Test
    public void testVersionParticipatesInEqualityAndPopulation() {
        MetaDTO first = new MetaDTO().setVersion("5.0.3-1");
        MetaDTO second = new MetaDTO().setVersion("5.0.4-1");

        assertNotEquals(first, second);
        second.populate(first);
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        second.populate(new MetaDTO());
        assertNull(second.getVersion());
        assertEquals("2.0.0", second.getManifestVersion());
    }


}
