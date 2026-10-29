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
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.config.TestConfig;
import org.candlepin.util.ObjectMapperFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class ProductDTOTest {

    @Test
    public void testMutableFields() {
        String derived = "derived-id";

        ContentDTO content = new ContentDTO()
            .setId("content-id");

        BrandingDTO branding = new BrandingDTO()
            .setId("branding-id");

        Map<String, String> attributes = Map.of("arch", "x86_64");
        Map<String, ContentDTO> productContent = Map.of("content-id", content);
        Set<BrandingDTO> brandings = Set.of(branding);

        ProductDTO dto = new ProductDTO()
            .setId("product-id")
            .setName("product-name")
            .setAttributes(attributes)
            .setDerivedProduct(derived)
            .setProvidedProducts(Set.of(derived))
            .setProductContent(productContent)
            .setBranding(brandings);

        assertEquals("product-id", dto.getId());
        assertEquals("product-name", dto.getName());
        assertEquals(attributes, dto.getAttributes());
        assertEquals(derived, dto.getDerivedProduct());
        assertEquals(Set.of(derived), dto.getProvidedProducts());
        assertEquals(productContent, dto.getProductContent());
        assertEquals(brandings, dto.getBranding());
    }

    @Test
    public void testEmptyDto() {
        ProductDTO dto = new ProductDTO();

        assertNull(dto.getId());
        assertNull(dto.getName());
        assertNull(dto.getAttributes());
        assertNull(dto.getDerivedProduct());
        assertNull(dto.getProvidedProducts());
        assertNull(dto.getProductContent());
        assertNull(dto.getBranding());
    }

    @Test
    void testProvidedProductCopyAndClone() {
        ProductDTO parent = new ProductDTO()
            .setId("parent")
            .setDerivedProduct("derived")
            .setProvidedProducts(Set.of("child"));

        ProductDTO copy = new ProductDTO(parent);
        ProductDTO clone = parent.clone();

        assertEquals(parent, copy);
        assertEquals(parent, clone);
        assertNotSame(parent, clone);
        copy.getProvidedProducts().clear();
        clone.setDerivedProduct("changed");
        assertEquals("derived", parent.getDerivedProduct());
        assertEquals(Set.of("child"), parent.getProvidedProducts());
        clone.getProvidedProducts().clear();
        assertEquals(1, parent.getProvidedProducts().size());
    }

    @Test
    void testProvidedProductsAreAnEncapsulatedView() {
        Set<String> source = new HashSet<>(Set.of("child"));
        ProductDTO dto = new ProductDTO().setProvidedProducts(source);

        source.clear();
        assertEquals(1, dto.getProvidedProducts().size());
        dto.getProvidedProducts().clear();
        assertTrue(dto.getProvidedProducts().isEmpty());
        dto.setProvidedProducts(null);
        assertNull(dto.getProvidedProducts());
    }

    @Test
    void testProvidedProductsUseIdsForEqualityAndHashing() {
        ProductDTO first = new ProductDTO().setProvidedProducts(List.of(
            "first", "second"));
        ProductDTO second = new ProductDTO().setProvidedProducts(List.of(
            "second", "first"));

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        second.setProvidedProducts(Set.of());
        assertNotEquals(first, second);
    }

    @Test
    void testRelatedProductsSerializeAsIds() {
        ProductDTO dto = new ProductDTO()
            .setId("parent")
            .setDerivedProduct("derived")
            .setProvidedProducts(List.of("provided", "provided"));
        ObjectMapper mapper = ObjectMapperFactory.getSyncObjectMapper(TestConfig.defaults());

        String json = mapper.writeValueAsString(dto);
        Map<String, Object> properties = mapper.readValue(json, Map.class);

        assertEquals("derived", properties.get("derivedProduct"));
        assertEquals(List.of("provided"), properties.get("providedProducts"));
        assertEquals(dto, mapper.readValue(json, ProductDTO.class));
    }

    @Test
    void testDerivedProductUsesIdForEqualityAndCanBeCleared() {
        ProductDTO first = new ProductDTO().setDerivedProduct("derived");
        ProductDTO second = new ProductDTO().setDerivedProduct("derived");

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        second.setDerivedProduct("other");
        assertNotEquals(first, second);
        second.setDerivedProduct(null);
        assertNull(second.getDerivedProduct());
    }

    static Stream<Collection<String>> invalidProvidedProducts() {
        return Stream.of(Arrays.asList((String) null), List.of(""));
    }

    @ParameterizedTest
    @MethodSource("invalidProvidedProducts")
    void testInvalidProvidedProducts(Collection<String> providedProducts) {
        ProductDTO dto = new ProductDTO();

        assertThrows(IllegalArgumentException.class, () -> dto.setProvidedProducts(providedProducts));
    }
}
