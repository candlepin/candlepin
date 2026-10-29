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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.config.TestConfig;
import org.candlepin.dto.CandlepinDTO;
import org.candlepin.dto.TimestampedCandlepinDTO;
import org.candlepin.util.ObjectMapperFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.databind.ObjectMapper;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

class ManifestDTOTest {

    static Stream<CandlepinDTO<?>> populatedDtos() {
        return Stream.<CandlepinDTO<?>>of(
            new BrandingDTO().setId("branding").setProductId("product").setName("name").setType("type"),
            new ConsumerDTO()
                .setUuid("consumer")
                .setName("name")
                .setOwnerId("owner")
                .setType(new ConsumerTypeDTO().setId("type").setLabel("manifest"))
                .setUrlWeb("https://web")
                .setUrlApi("https://api"),
            new ConsumerTypeDTO().setId("type").setLabel("manifest"),
            new ContentDTO()
                .setEnabled(true)
                .setId("content")
                .setName("name")
                .setType("yum")
                .setLabel("label")
                .setVendor("vendor")
                .setContentUrl("/content")
                .setGpgUrl("/gpg")
                .setReleaseVer("10")
                .setRequiredTags("tag")
                .setArches("x86_64"),
            new MetaDTO()
                .setCreated(new Date(1000L))
                .setPrincipalName("principal")
                .setWebAppPrefix("https://web")
                .setCdnLabel("cdn"),
            new ProductDTO()
                .setId("product")
                .setName("name")
                .setAttributes(Map.of("arch", "x86_64"))
                .setDerivedProduct("derived")
                .setProvidedProducts(Set.of("provided"))
                .setProductContent(Map.of("content", new ContentDTO().setId("content")))
                .setBranding(Set.of(new BrandingDTO().setId("branding"))),
            new SubscriptionDTO()
                .setSubscriptionId("subscription")
                .setProductId("product")
                .setStartDate(new Date(1000L))
                .setEndDate(new Date(2000L))
                .setAccountNumber("account")
                .setContractNumber("contract"))
            .peek(dto -> {
                if (dto instanceof TimestampedCandlepinDTO<?> timestamped) {
                    timestamped.setCreated(new Date(3000L));
                    timestamped.setUpdated(new Date(4000L));
                }
            });
    }

    @ParameterizedTest
    @MethodSource("populatedDtos")
    void testCloneEquality(CandlepinDTO<?> dto) {
        CandlepinDTO<?> copy = dto.clone();

        assertNotSame(dto, copy);
        assertEquals(dto, copy);
        assertEquals(copy, dto);
        assertEquals(dto.hashCode(), copy.hashCode());
        assertFalse(dto.equals(null));
        assertFalse(dto.equals(new Object()));
    }

    @ParameterizedTest
    @MethodSource("populatedDtos")
    void testCopyConstructor(CandlepinDTO<?> dto) throws ReflectiveOperationException {
        Object copy = dto.getClass().getConstructor(dto.getClass()).newInstance(dto);

        assertNotSame(dto, copy);
        assertEquals(dto, copy);
    }

    @ParameterizedTest
    @MethodSource("populatedDtos")
    void testPopulateRejectsNull(CandlepinDTO<?> dto) {
        assertThrows(IllegalArgumentException.class, () -> dto.populate(null));
    }

    @ParameterizedTest
    @MethodSource("populatedDtos")
    void testJsonRoundTrip(CandlepinDTO<?> dto) {
        ObjectMapper mapper = ObjectMapperFactory.getSyncObjectMapper(TestConfig.defaults());

        String json = mapper.writeValueAsString(dto);
        Object restored = mapper.readValue(json, dto.getClass());

        assertEquals(dto, restored);
        assertFalse(json.contains("serialVersionUID"));
        if (dto instanceof TimestampedCandlepinDTO<?>) {
            assertTrue(json.contains("\"created\""));
            assertTrue(json.contains("\"updated\""));
        }
    }

    @ParameterizedTest
    @MethodSource("populatedDtos")
    void testCloneHasIndependentTimestamps(CandlepinDTO<?> dto) {
        if (dto instanceof TimestampedCandlepinDTO<?> original) {
            TimestampedCandlepinDTO<?> copy = (TimestampedCandlepinDTO<?>) dto.clone();

            copy.getCreated().setTime(5000L);
            copy.getUpdated().setTime(6000L);

            assertEquals(new Date(3000L), original.getCreated());
            assertEquals(new Date(4000L), original.getUpdated());
            assertNotEquals(original, copy);
        }
    }

    @Test
    void testSubscriptionCloneHasIndependentDates() {
        SubscriptionDTO original = new SubscriptionDTO()
            .setStartDate(new Date(1000L))
            .setEndDate(new Date(2000L));
        SubscriptionDTO copy = original.clone();

        copy.getStartDate().setTime(3000L);
        copy.getEndDate().setTime(4000L);

        assertEquals(new Date(1000L), original.getStartDate());
        assertEquals(new Date(2000L), original.getEndDate());
    }

    @Test
    void testMetaCloneHasIndependentCreationDate() {
        MetaDTO original = new MetaDTO().setCreated(new Date(1000L));
        MetaDTO copy = original.clone();

        copy.getCreated().setTime(2000L);

        assertEquals(new Date(1000L), original.getCreated());
    }

    @Test
    void testConsumerCopyIsShallowAndCloneIsDeep() {
        ConsumerDTO original = new ConsumerDTO().setType(new ConsumerTypeDTO().setLabel("original"));
        ConsumerDTO shallowCopy = new ConsumerDTO(original);
        ConsumerDTO deepCopy = original.clone();

        assertSame(original.getType(), shallowCopy.getType());
        deepCopy.getType().setLabel("changed");

        assertEquals("original", original.getType().getLabel());
    }

    @Test
    void testProductCloneHasIndependentNestedObjects() {
        ProductDTO original = new ProductDTO()
            .setAttributes(Map.of("arch", "x86_64"))
            .setDerivedProduct("derived")
            .setProductContent(Map.of("content", new ContentDTO().setId("content").setName("original")))
            .setBranding(Set.of(new BrandingDTO().setId("branding").setName("original")));
        ProductDTO copy = original.clone();

        copy.getAttributes().clear();
        copy.setDerivedProduct("changed");
        copy.getProductContent().get("content").setName("changed");
        copy.getBranding().iterator().next().setName("changed");

        assertEquals(Map.of("arch", "x86_64"), original.getAttributes());
        assertEquals("derived", original.getDerivedProduct());
        assertEquals("original", original.getProductContent().get("content").getName());
        assertEquals("original", original.getBranding().iterator().next().getName());
    }

    @Test
    void testProductCollectionsAreEncapsulatedViews() {
        Map<String, String> attributes = new HashMap<>(Map.of("arch", "x86_64"));
        Map<String, ContentDTO> content = new HashMap<>(Map.of("content", new ContentDTO().setId("content")));
        Set<BrandingDTO> branding = new HashSet<>(Set.of(new BrandingDTO().setId("branding")));
        ProductDTO dto = new ProductDTO()
            .setAttributes(attributes)
            .setProductContent(content)
            .setBranding(branding);

        attributes.clear();
        content.clear();
        branding.clear();

        assertEquals(1, dto.getAttributes().size());
        assertEquals(1, dto.getProductContent().size());
        assertEquals(1, dto.getBranding().size());

        dto.getAttributes().clear();
        dto.getProductContent().clear();
        dto.getBranding().clear();

        assertTrue(dto.getAttributes().isEmpty());
        assertTrue(dto.getProductContent().isEmpty());
        assertTrue(dto.getBranding().isEmpty());
    }

    @Test
    void testProductCollectionsCanBeClearedWithNull() {
        ProductDTO dto = new ProductDTO()
            .setAttributes(Map.of())
            .setProductContent(Map.of())
            .setBranding(Set.of());

        dto.setAttributes(null);
        dto.setProductContent(null);
        dto.setBranding(null);

        assertNull(dto.getAttributes());
        assertNull(dto.getProductContent());
        assertNull(dto.getBranding());
    }
}
