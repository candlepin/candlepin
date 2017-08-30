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

import static org.junit.Assert.*;

import org.candlepin.dto.AbstractDTOTest;
import org.candlepin.dto.api.v1.ProductDTO.ProductContentDTO;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;



/**
 * Test suite for the ProductDTO class
 */
public class ProductDTOTest extends AbstractDTOTest<ProductDTO> {

    protected ContentDTOTest contentDTOTest = new ContentDTOTest();

    protected Map<String, Object> values;

    public ProductDTOTest() {
        super(ProductDTO.class);

        Collection<ProductContentDTO> productContent = new LinkedList<ProductContentDTO>();

        for (int i = 0; i < 5; ++i) {
            ContentDTO content = this.contentDTOTest.getPopulatedDTOInstance();
            content.setId(content.getId() + "-" + i);

            productContent.add(new ProductContentDTO(content, i % 2 != 0));
        }

        Map<String, String> attributes = new HashMap<String, String>();

        for (int i = 0; i < 5; ++i) {
            attributes.put("attrib-" + i, "value-" + i);
        }

        Map<String, ProductContentDTO> map = new HashMap<String, ProductContentDTO>();
        ContentDTO content = this.contentDTOTest.getPopulatedDTOInstance();
        ProductContentDTO pcdto = new ProductContentDTO(content, true);
        map.put(content.getId(), pcdto);

        this.values = new HashMap<String, Object>();
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
    }

    /**
     * @{inheritDocs}
     */
    @Override
    protected Object getInputValueForMutator(String field) {
        return this.values.get(field);
    }

    /**
     * @{inheritDocs}
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

    @Test(expected = IllegalArgumentException.class)
    public void testHasAttributeWithNullInput() {
        ProductDTO dto = new ProductDTO();
        dto.hasAttribute(null);
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

    @Test(expected = IllegalArgumentException.class)
    public void testGetAttributeWithNullAttribute() {
        ProductDTO dto = new ProductDTO();
        dto.getAttributeValue(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSetAttributeWithNullAttribute() {
        ProductDTO dto = new ProductDTO();
        dto.setAttribute(null, "value");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testHasAttributeWithNullAttribute() {
        ProductDTO dto = new ProductDTO();
        dto.hasAttribute(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRemoveAttributeWithNullAttribute() {
        ProductDTO dto = new ProductDTO();
        dto.removeAttribute(null);
    }
}
