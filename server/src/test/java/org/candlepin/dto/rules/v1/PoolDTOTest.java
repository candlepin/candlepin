/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
package org.candlepin.dto.rules.v1;

import org.candlepin.dto.AbstractDTOTest;
import org.junit.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Test suite for the PoolDTO (Rules) class
 */
public class PoolDTOTest extends AbstractDTOTest<PoolDTO> {

    protected Map<String, Object> values;

    public PoolDTOTest() {
        super(PoolDTO.class);

        PoolDTO.ProvidedProductDTO providedProd =
            new PoolDTO.ProvidedProductDTO("provided-product-id", "provided-product-name");

        PoolDTO.ProvidedProductDTO derivedProvidedProd =
            new PoolDTO.ProvidedProductDTO("derived-provided-product-id", "derived-provided-product-name");

        this.values = new HashMap<>();
        this.values.put("Id", "test-id");
        this.values.put("Quantity", 1L);
        this.values.put("StartDate", new Date());
        this.values.put("EndDate", new Date());

        Map<String, String> attributes = new HashMap<>();
        attributes.put("attribute-key-1", "attribute-value-1");
        attributes.put("attribute-key-2", "attribute-value-2");
        this.values.put("Attributes", attributes);

        this.values.put("RestrictedToUsername", "restricted-to-username-value");
        this.values.put("Consumed", 5L);

        this.values.put("ProductId", "product-id-1");

        Map<String, String> productAttributes = new HashMap<>();
        productAttributes.put("prod-attribute-key-1", "prod-attribute-value-1");
        productAttributes.put("prod-attribute-key-2", "prod-attribute-value-2");
        this.values.put("ProductAttributes", productAttributes);

        this.values.put("DerivedProductId", "derived-prod-id-1");

        Set<PoolDTO.ProvidedProductDTO> providedProducts = new HashSet<>();
        providedProducts.add(providedProd);
        this.values.put("ProvidedProducts", providedProducts);

        Set<PoolDTO.ProvidedProductDTO> derivedProvidedProducts = new HashSet<>();
        derivedProvidedProducts.add(derivedProvidedProd);
        this.values.put("DerivedProvidedProducts", derivedProvidedProducts);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object getInputValueForMutator(String field) {
        return this.values.get(field);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Object getOutputValueForAccessor(String field, Object input) {
        // Nothing to do here
        return input;
    }

    @Test
    public void testHasAttributeWithAbsentAttribute() {
        PoolDTO dto = new PoolDTO();
        assertFalse(dto.hasAttribute("attribute-key-1"));
    }

    @Test
    public void testHasAttributeWithPresentAttribute() {
        PoolDTO dto = new PoolDTO();
        Map<String, String> attributes = new HashMap<>();
        attributes.put("attribute-key-2", "attribute-value-2");
        dto.setAttributes(attributes);

        assertTrue(dto.hasAttribute("attribute-key-2"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHasAttributeWithNullInput() {
        PoolDTO dto = new PoolDTO();
        dto.hasAttribute(null);
    }

    @Test
    public void testRemoveAttributeWithAbsentAttribute() {
        PoolDTO dto = new PoolDTO();

        assertFalse(dto.removeAttribute("attribute-key-3"));

        dto.setAttributes(new HashMap<>());

        assertFalse(dto.removeAttribute("attribute-key-3"));
    }

    @Test
    public void testRemoveAttributeWithPresentAttribute() {
        PoolDTO dto = new PoolDTO();

        Map<String, String> attributes = new HashMap<>();
        attributes.put("attribute-key-4", "attribute-value-4");
        dto.setAttributes(attributes);

        assertTrue(dto.removeAttribute("attribute-key-4"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRemoveAttributeWithNullInput() {
        PoolDTO dto = new PoolDTO();
        dto.removeAttribute(null);
    }

    @Test
    public void testHasProductAttributeWithAbsentProductAttribute() {
        PoolDTO dto = new PoolDTO();
        assertFalse(dto.hasProductAttribute("prod-attribute-key-1"));
    }

    @Test
    public void testHasProductAttributeWithPresentProductAttribute() {
        PoolDTO dto = new PoolDTO();
        Map<String, String> productAttributes = new HashMap<>();
        productAttributes.put("prod-attribute-key-2", "prod-attribute-value-2");
        dto.setProductAttributes(productAttributes);

        assertTrue(dto.hasProductAttribute("prod-attribute-key-2"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHasProductAttributeWithNullInput() {
        PoolDTO dto = new PoolDTO();
        dto.hasProductAttribute(null);
    }

    @Test
    public void testAddProvidedProductWithAbsentProvidedProduct() {
        PoolDTO dto = new PoolDTO();

        PoolDTO.ProvidedProductDTO product =
            new PoolDTO.ProvidedProductDTO("test-id-provided-product-1", "test-name-provided-product-1");
        assertTrue(dto.addProvidedProduct(product));
    }

    @Test
    public void testAddProvidedProductWithPresentProvidedProduct() {
        PoolDTO dto = new PoolDTO();

        PoolDTO.ProvidedProductDTO product =
            new PoolDTO.ProvidedProductDTO("test-id-provided-product-2", "test-name-provided-product-2");
        assertTrue(dto.addProvidedProduct(product));

        PoolDTO.ProvidedProductDTO product2 =
            new PoolDTO.ProvidedProductDTO("test-id-provided-product-2", "test-name-provided-product-2");
        assertFalse(dto.addProvidedProduct(product2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddProvidedProductWithNullInput() {
        PoolDTO dto = new PoolDTO();
        dto.addProvidedProduct(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddProvidedProductWithEmptyId() {
        PoolDTO dto = new PoolDTO();

        PoolDTO.ProvidedProductDTO product =
            new PoolDTO.ProvidedProductDTO("", "test-name-provided-product-3");
        dto.addProvidedProduct(product);
    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void testAddDerivedProvidedProductWithAbsentDerivedProvidedProduct() {
        PoolDTO dto = new PoolDTO();

        PoolDTO.ProvidedProductDTO product =
                new PoolDTO.ProvidedProductDTO(
                        "test-id-derived-provided-product-1",
                        "test-name-derived-provided-product-1");
        assertTrue(dto.addDerivedProvidedProduct(product));
    }

    @Test
    @SuppressWarnings("checkstyle:indentation")
    public void testAddDerivedProvidedProductWithPresentDerivedProvidedProduct() {
        PoolDTO dto = new PoolDTO();

        PoolDTO.ProvidedProductDTO product =
            new PoolDTO.ProvidedProductDTO(
            "test-id-derived-provided-product-2",
            "test-name-derived-provided-product-2");
        assertTrue(dto.addDerivedProvidedProduct(product));

        PoolDTO.ProvidedProductDTO product2 =
            new PoolDTO.ProvidedProductDTO(
            "test-id-derived-provided-product-2",
            "test-name-derived-provided-product-2");
        assertFalse(dto.addDerivedProvidedProduct(product2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddDerivedProvidedProductWithNullInput() {
        PoolDTO dto = new PoolDTO();
        dto.addDerivedProvidedProduct(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddDerivedProvidedProductWithEmptyId() {
        PoolDTO dto = new PoolDTO();

        PoolDTO.ProvidedProductDTO product =
            new PoolDTO.ProvidedProductDTO("", "test-name-derived-provided-product-3");
        dto.addDerivedProvidedProduct(product);
    }
}
