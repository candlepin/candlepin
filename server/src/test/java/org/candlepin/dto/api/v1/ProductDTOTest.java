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

import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.dto.AbstractDTOTest;
import org.candlepin.dto.api.v1.ProductDTO.ProductContentDTO;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;



/**
 * Test suite for the ProductDTO class
 */
public class ProductDTOTest extends AbstractDTOTest<ProductDTO> {

    protected ContentDTOTest contentDTOTest = new ContentDTOTest();

    protected Map<String, Object> values;

    public ProductDTOTest() {
        super(ProductDTO.class);

        Collection<ProductContentDTO> productContent = new LinkedList<>();

        for (int i = 0; i < 5; ++i) {
            ContentDTO content = this.contentDTOTest.getPopulatedDTOInstance();
            content.setId(content.getId() + "-" + i);

            productContent.add(new ProductContentDTO(content, i % 2 != 0));
        }

        Map<String, String> attributes = new HashMap<>();

        for (int i = 0; i < 5; ++i) {
            attributes.put("attrib-" + i, "value-" + i);
        }

        Collection<String> dependentProductIds = new LinkedList<>();

        for (int i = 0; i < 5; ++i) {
            dependentProductIds.add("dependentProdId" + i);
        }

        Collection<BrandingDTO> brandings = new HashSet<>();
        for (int i = 0; i < 5; ++i) {
            BrandingDTO branding = new BrandingDTO();
            branding.setProductId("prod_id_" + i);
            branding.setType("OS");
            branding.setName("Brand Name" + i);
            brandings.add(branding);
        }

        Set<ProductDTO> providedProd = Util.asSet(
            TestUtil.createProductDTO("pp1", "providedProduct1"),
            TestUtil.createProductDTO("pp2", "providedProduct2"),
            TestUtil.createProductDTO("pp3", "providedProduct3")
        );

        this.values = new HashMap<>();
        this.values.put("Uuid", "test_value");
        this.values.put("Id", "test_value");
        this.values.put("Type", "test_value");
        this.values.put("Label", "test_value");
        this.values.put("Name", "test_value");
        this.values.put("Vendor", "test_value");
        this.values.put("ProductUrl", "test_value");
        this.values.put("RequiredTags", "test_value");
        this.values.put("ReleaseVersion", "test_value");
        this.values.put("GpgUrl", "test_value");
        this.values.put("MetadataExpire", 1234L);
        this.values.put("ModifiedProductIds", Arrays.asList("1", "2", "3"));
        this.values.put("Arches", "test_value");
        this.values.put("Locked", Boolean.TRUE);
        this.values.put("ProductContent", productContent);
        this.values.put("Attributes", attributes);
        this.values.put("DependentProductIds", dependentProductIds);
        this.values.put("Multiplier", 3L);
        this.values.put("Branding", brandings);
        this.values.put("Href", "test-href");
        this.values.put("Created", new Date());
        this.values.put("Updated", new Date());
        this.values.put("ProvidedProducts", providedProd);
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
        ProductDTO dto = new ProductDTO();
        assertFalse(dto.hasAttribute("attrib"));
    }

    @Test
    public void testHasAttributeWithPresentAttribute() {
        ProductDTO dto = new ProductDTO();
        dto.setAttribute("attrib", "value");

        assertTrue(dto.hasAttribute("attrib"));
    }

    @Test
    public void testAttributeCRUDOps() {
        ProductDTO dto = new ProductDTO();
        String attrib = "test-attrib";

        assertFalse(dto.hasAttribute(attrib));
        String value = dto.getAttributeValue(attrib);
        assertNull(value);

        dto.setAttribute(attrib, "val");

        assertTrue(dto.hasAttribute(attrib));
        value = dto.getAttributeValue(attrib);
        assertEquals("val", value);

        dto.setAttribute(attrib, null);

        assertTrue(dto.hasAttribute(attrib));
        value = dto.getAttributeValue(attrib);
        assertNull(value);

        dto.removeAttribute(attrib);

        assertFalse(dto.hasAttribute(attrib));
        value = dto.getAttributeValue(attrib);
        assertNull(value);
    }

    @Test
    public void testGetAttributeWithNullAttribute() {
        ProductDTO dto = new ProductDTO();
        assertThrows(IllegalArgumentException.class, () -> dto.getAttributeValue(null));
    }

    @Test
    public void testSetAttributeWithNullAttribute() {
        ProductDTO dto = new ProductDTO();
        assertThrows(IllegalArgumentException.class, () -> dto.setAttribute(null, "value"));
    }

    @Test
    public void testHasAttributeWithNullAttribute() {
        ProductDTO dto = new ProductDTO();
        assertThrows(IllegalArgumentException.class, () -> dto.hasAttribute(null));
    }

    @Test
    public void testRemoveAttributeWithNullAttribute() {
        ProductDTO dto = new ProductDTO();
        assertThrows(IllegalArgumentException.class, () -> dto.removeAttribute(null));
    }

    @Test
    public void testAddBrandingWithAbsentBranding() {
        ProductDTO dto = new ProductDTO();

        BrandingDTO branding = new BrandingDTO();
        branding.setProductId("test-branding-product-id-1");
        branding.setName("test-branding-name-1");
        branding.setType("test-branding-type-1");
        assertTrue(dto.addBranding(branding));
    }

    @Test
    public void testAddBrandingWithPresentBranding() {
        ProductDTO dto = new ProductDTO();

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

    @Test
    public void testAddBrandingWithNullInput() {
        ProductDTO dto = new ProductDTO();
        assertThrows(IllegalArgumentException.class, () -> dto.addBranding(null));
    }

    @Test
    public void testAddBrandingWithEmptyProductId() {
        ProductDTO dto = new ProductDTO();

        BrandingDTO branding = new BrandingDTO();
        branding.setProductId("");
        branding.setName("test-branding-name-3");
        branding.setType("test-branding-type-3");
        assertThrows(IllegalArgumentException.class, () -> dto.addBranding(branding));
    }

    @Test
    public void testAddBrandingWithEmptyName() {
        ProductDTO dto = new ProductDTO();

        BrandingDTO branding = new BrandingDTO();
        branding.setProductId("test-branding-product-id-4");
        branding.setName("");
        branding.setType("test-branding-type-4");
        assertThrows(IllegalArgumentException.class, () -> dto.addBranding(branding));
    }

    @Test
    public void testAddBrandingWithEmptyType() {
        ProductDTO dto = new ProductDTO();

        BrandingDTO branding = new BrandingDTO();
        branding.setProductId("test-branding-product-id-5");
        branding.setName("test-branding-name-5");
        branding.setType("");
        assertThrows(IllegalArgumentException.class, () -> dto.addBranding(branding));
    }

    @Test
    public void testAddProvidedProductWithAbsentProvidedProduct() {
        ProductDTO dto = new ProductDTO();
        ProductDTO prov1 = TestUtil.createProductDTO("prodID2", "OS");

        assertTrue(dto.addProvidedProduct(prov1));
    }

    @Test
    public void testAddProvidedProductWithPresentProvidedProduct() {
        ProductDTO dto = new ProductDTO();
        ProductDTO provProduct = TestUtil.createProductDTO("prodID2", "OS");

        assertTrue(dto.addProvidedProduct(provProduct));

        ProductDTO anotherProvProduct = TestUtil.createProductDTO("prodID2", "OS");

        assertFalse(dto.addProvidedProduct(anotherProvProduct));
    }

    @Test
    public void testAddProvidedProductWithNullInput() {
        ProductDTO dto = new ProductDTO();
        assertThrows(IllegalArgumentException.class, () -> dto.addProvidedProduct(null));
    }

}
