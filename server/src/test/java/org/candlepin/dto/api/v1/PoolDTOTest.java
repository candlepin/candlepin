/**
 * Copyright (c) 2009 - 2017 Red Hat, Inc.
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
package org.candlepin.dto.api.v1;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.candlepin.dto.AbstractDTOTest;
import org.candlepin.model.Pool;

import org.junit.Test;

import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Test suite for the PoolDTO class
 */
public class PoolDTOTest extends AbstractDTOTest<PoolDTO> {

    protected Map<String, Object> values;

    public PoolDTOTest() {
        super(PoolDTO.class);

        NestedOwnerDTO owner = new NestedOwnerDTO();
        owner.setId("owner-id");
        owner.setKey("owner-key");
        owner.setDisplayName("owner-name");
        EntitlementDTO entitlement = new EntitlementDTO();
        entitlement.setId("entitlement-id");

        BrandingDTO branding = new BrandingDTO();
        branding.setName("branding-name");
        branding.setProductId("branding-prod-id");
        branding.setType("branding-type");

        PoolDTO.ProvidedProductDTO providedProd =
            new PoolDTO.ProvidedProductDTO("provided-product-id", "provided-product-name");

        PoolDTO.ProvidedProductDTO derivedProvidedProd =
            new PoolDTO.ProvidedProductDTO("derived-provided-product-id", "derived-provided-product-name");

        CertificateDTO cert = new CertificateDTO();
        cert.setId("cert-id");
        cert.setKey("cert-key");
        cert.setCert("cert-cert");
        cert.setSerial(new CertificateSerialDTO());

        this.values = new HashMap<>();
        this.values.put("Id", "test-id");
        this.values.put("Type", Pool.PoolType.NORMAL.toString());
        this.values.put("Owner", owner);
        this.values.put("ActiveSubscription", true);
        this.values.put("SourceEntitlement", entitlement);
        this.values.put("Quantity", 1L);
        this.values.put("StartDate", new Date());
        this.values.put("EndDate", new Date());

        Map<String, String> attributes = new HashMap<>();
        attributes.put("attribute-key-1", "attribute-value-1");
        attributes.put("attribute-key-2", "attribute-value-2");
        this.values.put("Attributes", attributes);

        this.values.put("RestrictedToUsername", "restricted-to-username-value");
        this.values.put("ContractNumber", "2991");
        this.values.put("AccountNumber", "2992");
        this.values.put("OrderNumber", "2993");
        this.values.put("Consumed", 5L);
        this.values.put("Exported", 4L);

        Set<BrandingDTO> brandingSet = new HashSet<>();
        brandingSet.add(branding);
        this.values.put("Branding", brandingSet);

        Map<String, String> calculatedAttributes = new HashMap<>();
        calculatedAttributes.put("calc-attribute-key-1", "calc-attribute-value-1");
        calculatedAttributes.put("calc-attribute-key-2", "calc-attribute-value-2");
        this.values.put("CalculatedAttributes", calculatedAttributes);

        this.values.put("UpstreamPoolId", "upstream-pool-id-1");
        this.values.put("UpstreamEntitlementId", "upstream-entitlement-id-1");
        this.values.put("UpstreamConsumerId", "upstream-consumer-id-1");
        this.values.put("ProductName", "product-name-1");
        this.values.put("ProductId", "product-id-1");

        Map<String, String> productAttributes = new HashMap<>();
        productAttributes.put("prod-attribute-key-1", "prod-attribute-value-1");
        productAttributes.put("prod-attribute-key-2", "prod-attribute-value-2");
        this.values.put("ProductAttributes", productAttributes);

        this.values.put("StackId", "stack-id-1");
        this.values.put("Stacked", true);
        this.values.put("DevelopmentPool", true);

        Map<String, String> derivedProductAttributes = new HashMap<>();
        derivedProductAttributes.put("derived-prod-attribute-key-1", "derived-prod-attribute-value-1");
        derivedProductAttributes.put("derived-prod-attribute-key-2", "derived-prod-attribute-value-2");
        this.values.put("DerivedProductAttributes", derivedProductAttributes);

        this.values.put("DerivedProductId", "derived-prod-id-1");
        this.values.put("DerivedProductName", "derived-prod-name-1");

        Set<PoolDTO.ProvidedProductDTO> providedProducts = new HashSet<>();
        providedProducts.add(providedProd);
        this.values.put("ProvidedProducts", providedProducts);

        Set<PoolDTO.ProvidedProductDTO> derivedProvidedProducts = new HashSet<>();
        derivedProvidedProducts.add(derivedProvidedProd);
        this.values.put("DerivedProvidedProducts", derivedProvidedProducts);
        this.values.put("SourceStackId", "source-stack-id-1");

        this.values.put("SubscriptionSubKey", "subscription-key-1");
        this.values.put("SubscriptionId", "subscription-id-1");

        this.values.put("Certificate", cert);
        this.values.put("Created", new Date());
        this.values.put("Updated", new Date());
        this.values.put("Locked", true);
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
