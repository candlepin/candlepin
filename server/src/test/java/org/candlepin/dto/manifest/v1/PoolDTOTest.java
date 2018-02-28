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
package org.candlepin.dto.manifest.v1;

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
 * Test suite for the PoolDTO (manifest import/export) class
 */
public class PoolDTOTest extends AbstractDTOTest<PoolDTO> {

    protected Map<String, Object> values;

    public PoolDTOTest() {
        super(PoolDTO.class);

        BrandingDTO branding = new BrandingDTO();
        branding.setName("branding-name");
        branding.setProductId("branding-prod-id");
        branding.setType("branding-type");

        PoolDTO.ProvidedProductDTO providedProd =
            new PoolDTO.ProvidedProductDTO("provided-product-id", "provided-product-name");

        PoolDTO.ProvidedProductDTO derivedProvidedProd =
            new PoolDTO.ProvidedProductDTO("derived-provided-product-id", "derived-provided-product-name");

        this.values = new HashMap<>();
        this.values.put("Id", "test-id");

        this.values.put("ContractNumber", "2991");
        this.values.put("AccountNumber", "2992");
        this.values.put("OrderNumber", "2993");

        Set<BrandingDTO> brandingSet = new HashSet<>();
        brandingSet.add(branding);
        this.values.put("Branding", brandingSet);

        this.values.put("ProductId", "product-id-1");
        this.values.put("DerivedProductId", "derived-prod-id-1");

        Set<PoolDTO.ProvidedProductDTO> providedProducts = new HashSet<>();
        providedProducts.add(providedProd);
        this.values.put("ProvidedProducts", providedProducts);

        Set<PoolDTO.ProvidedProductDTO> derivedProvidedProducts = new HashSet<>();
        derivedProvidedProducts.add(derivedProvidedProd);
        this.values.put("DerivedProvidedProducts", derivedProvidedProducts);

        this.values.put("StartDate", new Date());
        this.values.put("EndDate", new Date());
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

    @Test
    public void testAddBrandingWithAbsentBranding() {
        PoolDTO dto = new PoolDTO();

        BrandingDTO branding = new BrandingDTO();
        branding.setProductId("test-branding-product-id-1");
        branding.setName("test-branding-name-1");
        branding.setType("test-branding-type-1");
        assertTrue(dto.addBranding(branding));
    }

    @Test
    public void testAddBrandingWithPresentBranding() {
        PoolDTO dto = new PoolDTO();

        BrandingDTO branding = new BrandingDTO();
        branding.setProductId("test-branding-product-id-2");
        branding.setName("test-branding-name-2");
        branding.setType("test-branding-type-2");
        assertTrue(dto.addBranding(branding));

        BrandingDTO branding2 = new BrandingDTO();
        branding2.setProductId("test-branding-product-id-2");
        branding2.setName("test-branding-name-2");
        branding2.setType("test-branding-type-2");
        assertFalse(dto.addBranding(branding2));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddBrandingWithNullInput() {
        PoolDTO dto = new PoolDTO();
        dto.addBranding(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddBrandingWithEmptyProductId() {
        PoolDTO dto = new PoolDTO();

        BrandingDTO branding = new BrandingDTO();
        branding.setProductId("");
        branding.setName("test-branding-name-3");
        branding.setType("test-branding-type-3");
        dto.addBranding(branding);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddBrandingWithEmptyName() {
        PoolDTO dto = new PoolDTO();

        BrandingDTO branding = new BrandingDTO();
        branding.setProductId("test-branding-product-id-4");
        branding.setName("");
        branding.setType("test-branding-type-4");
        dto.addBranding(branding);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testAddBrandingWithEmptyType() {
        PoolDTO dto = new PoolDTO();

        BrandingDTO branding = new BrandingDTO();
        branding.setProductId("test-branding-product-id-5");
        branding.setName("test-branding-name-5");
        branding.setType("");
        dto.addBranding(branding);
    }
}
