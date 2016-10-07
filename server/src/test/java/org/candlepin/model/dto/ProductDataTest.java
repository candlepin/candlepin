/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.model.dto;

import org.candlepin.model.Content;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.ProductContent;
import org.candlepin.util.Util;

import static org.junit.Assert.*;

import junitparams.JUnitParamsRunner;
import junitparams.Parameters;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;



/**
 * Test suite for the ProductData class
 */
@RunWith(JUnitParamsRunner.class)
public class ProductDataTest {

    // public .* (?:get|is)(.*)\(\) {\npublic .* set\1\(.*\) {

    // @Test
    // public void testGetSet\1() {
    //     ProductData dto = new ProductData();
    //     String input = "test_value";

    //     String output = dto.get\1();
    //     assertNull(output);

    //     ProductData output2 = dto.set\1(input);
    //     assertSame(output2, dto);

    //     output = dto.get\1();
    //     assertEquals(input, output);
    // }

    protected Object[][] getBadStringValues() {
        return new Object[][] {
            new Object[] { null },
            new Object[] { "" },
        };
    }

    @Test
    public void testGetSetUuid() {
        ProductData dto = new ProductData();
        String input = "test_value";

        String output = dto.getUuid();
        assertNull(output);

        ProductData output2 = dto.setUuid(input);
        assertSame(output2, dto);

        output = dto.getUuid();
        assertEquals(input, output);
    }

    @Test
    public void testGetSetId() {
        ProductData dto = new ProductData();
        String input = "test_value";

        String output = dto.getId();
        assertNull(output);

        ProductData output2 = dto.setId(input);
        assertSame(output2, dto);

        output = dto.getId();
        assertEquals(input, output);
    }

    @Test(expected = IllegalArgumentException.class)
    @Parameters(method = "getBadStringValues")
    public void testGetSetIdBadValues(String input) {
        ProductData dto = new ProductData();

        String output = dto.getId();
        assertNull(output);

        dto.setId(input);
    }

    @Test
    public void testGetSetName() {
        ProductData dto = new ProductData();
        String input = "test_value";

        String output = dto.getName();
        assertNull(output);

        ProductData output2 = dto.setName(input);
        assertSame(output2, dto);

        output = dto.getName();
        assertEquals(input, output);
    }

    @Test
    public void testGetSetMultiplier() {
        ProductData dto = new ProductData();
        Long input = 1234L;

        Long output = dto.getMultiplier();
        assertNull(output);

        ProductData output2 = dto.setMultiplier(input);
        assertSame(output2, dto);

        output = dto.getMultiplier();
        assertEquals(input, output);
    }

    @Test
    public void testGetSetAttributes() {
        ProductData dto = new ProductData();
        Collection<ProductAttributeData> input = Arrays.asList(
            new ProductAttributeData("a1", "v1"),
            new ProductAttributeData("a2", "v2"),
            new ProductAttributeData("a3", "v3")
        );

        Collection<ProductAttributeData> input2 = Arrays.asList(
            new ProductAttributeData("a1", "old_value"),
            new ProductAttributeData("a1", "v1"),
            new ProductAttributeData("a2", "old_value"),
            new ProductAttributeData("a2", "v2"),
            new ProductAttributeData("a3", "old_value"),
            new ProductAttributeData("a3", "v3")
        );

        Collection<ProductAttributeData> output = dto.getAttributes();
        assertNull(output);

        ProductData output2 = dto.setAttributes(input);
        assertSame(dto, output2);

        output = dto.getAttributes();
        assertEquals(input, output);

        // Second pass to ensure setAttributes is actually clearing existing attributes before
        // adding new ones
        output2 = dto.setAttributes(input);
        assertSame(dto, output2);

        output = dto.getAttributes();
        assertEquals(input, output);

        // Third pass to ensure setting duplicates doesn't allow the dupes to be retained
        output2 = dto.setAttributes(input2);
        assertSame(dto, output2);

        output = dto.getAttributes();
        assertEquals(input, output);
    }

    @Test
    public void testGetAttribute() {
        ProductData dto = new ProductData();
        ProductAttributeData attrib1 = new ProductAttributeData("a1", "v1");
        ProductAttributeData attrib2 = new ProductAttributeData("a2", "v2");
        ProductAttributeData attrib3 = new ProductAttributeData("a3", "v3");

        ProductAttributeData output = dto.getAttribute("a1");
        assertNull(output);

        dto.setAttributes(Arrays.asList(attrib1, attrib2, attrib3));
        output = dto.getAttribute("a1");
        assertEquals(attrib1, output);

        output = dto.getAttribute("a3");
        assertEquals(attrib3, output);

        output = dto.getAttribute("a4");
        assertNull(output);
    }

    @Test
    public void testGetAttributeValue() {
        ProductData dto = new ProductData();
        Collection<ProductAttributeData> input = Arrays.asList(
            new ProductAttributeData("a1", "v1"),
            new ProductAttributeData("a2", "v2"),
            new ProductAttributeData("a3", "v3")
        );

        String output = dto.getAttributeValue("a1");
        assertNull(output);

        dto.setAttributes(input);
        output = dto.getAttributeValue("a1");
        assertEquals("v1", output);

        output = dto.getAttributeValue("a3");
        assertEquals("v3", output);

        output = dto.getAttributeValue("a4");
        assertNull(output);
    }

    @Test
    public void testHasAttribute() {
        ProductData dto = new ProductData();
        Collection<ProductAttributeData> input = Arrays.asList(
            new ProductAttributeData("a1", "v1"),
            new ProductAttributeData("a2", "v2"),
            new ProductAttributeData("a3", "v3")
        );

        boolean output = dto.hasAttribute("a1");
        assertFalse(output);

        dto.setAttributes(input);
        output = dto.hasAttribute("a1");
        assertTrue(output);

        output = dto.hasAttribute("a3");
        assertTrue(output);

        output = dto.hasAttribute("a4");
        assertFalse(output);
    }

    @Test
    public void testAddAttributeByObject() {
        ProductData dto = new ProductData();
        ProductAttributeData attrib1 = new ProductAttributeData("a1", "v1");
        ProductAttributeData attrib2 = new ProductAttributeData("a2", "v2");
        ProductAttributeData attrib3 = new ProductAttributeData("a2", "v3");

        assertNull(dto.getAttributes());

        boolean output = dto.addAttribute(attrib1);
        Collection<ProductAttributeData> output2 = dto.getAttributes();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(attrib1), output2));

        output = dto.addAttribute(attrib1);
        output2 = dto.getAttributes();
        assertFalse(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(attrib1), output2));

        output = dto.addAttribute(attrib2);
        output2 = dto.getAttributes();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(attrib1, attrib2), output2));

        output = dto.addAttribute(attrib3);
        output2 = dto.getAttributes();
        assertTrue(output);
        assertEquals(Arrays.asList(attrib1, attrib3), output2);
    }

    @Test
    public void testSetAttributeByValue() {
        ProductData dto = new ProductData();
        ProductAttributeData attrib1 = new ProductAttributeData("a1", "v1");
        ProductAttributeData attrib2 = new ProductAttributeData("a2", "v2");
        ProductAttributeData attrib3 = new ProductAttributeData("a2", "v3");

        assertNull(dto.getAttributes());

        boolean output = dto.setAttribute(attrib1.getName(), attrib1.getValue());
        Collection<ProductAttributeData> output2 = dto.getAttributes();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(attrib1), output2));

        output = dto.setAttribute(attrib1.getName(), attrib1.getValue());
        output2 = dto.getAttributes();
        assertFalse(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(attrib1), output2));

        output = dto.setAttribute(attrib2.getName(), attrib2.getValue());
        output2 = dto.getAttributes();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(attrib1, attrib2), output2));

        output = dto.setAttribute(attrib3.getName(), attrib3.getValue());
        output2 = dto.getAttributes();
        assertTrue(output);
        assertEquals(Arrays.asList(attrib1, attrib3), output2);
    }

    @Test
    public void testRemoveAttributeByObject() {
        ProductData dto = new ProductData();
        ProductAttributeData attrib1 = new ProductAttributeData("a1", "v1");
        ProductAttributeData attrib2 = new ProductAttributeData("a2", "v2");
        ProductAttributeData attrib3 = new ProductAttributeData("a2", "v3");

        assertNull(dto.getAttributes());
        assertFalse(dto.removeAttribute(attrib1));
        assertFalse(dto.removeAttribute(attrib2));
        assertFalse(dto.removeAttribute(attrib3));

        dto.setAttributes(Arrays.asList(attrib1, attrib2));

        boolean output = dto.removeAttribute(attrib1);
        Collection<ProductAttributeData> output2 = dto.getAttributes();
        assertTrue(output);
        assertEquals(Arrays.asList(attrib2), output2);

        output = dto.removeAttribute(attrib1);
        output2 = dto.getAttributes();
        assertFalse(output);
        assertEquals(Arrays.asList(attrib2), output2);

        // This should work because we remove by attribute key, not by exact element match
        // Also note that the collection should not be nulled by removing the final element
        output = dto.removeAttribute(attrib3);
        output2 = dto.getAttributes();
        assertTrue(output);
        assertEquals(Arrays.asList(), output2);
    }

    @Test
    public void testRemoveAttributeByName() {
        ProductData dto = new ProductData();
        ProductAttributeData attrib1 = new ProductAttributeData("a1", "v1");
        ProductAttributeData attrib2 = new ProductAttributeData("a2", "v2");
        ProductAttributeData attrib3 = new ProductAttributeData("a2", "v3");

        assertNull(dto.getAttributes());
        assertFalse(dto.removeAttribute(attrib1.getName()));
        assertFalse(dto.removeAttribute(attrib2.getName()));
        assertFalse(dto.removeAttribute(attrib3.getName()));

        dto.setAttributes(Arrays.asList(attrib1, attrib2));

        boolean output = dto.removeAttribute(attrib1.getName());
        Collection<ProductAttributeData> output2 = dto.getAttributes();
        assertTrue(output);
        assertEquals(Arrays.asList(attrib2), output2);

        output = dto.removeAttribute(attrib1.getName());
        output2 = dto.getAttributes();
        assertFalse(output);
        assertEquals(Arrays.asList(attrib2), output2);

        // Note that the collection should not be nulled by removing the final element
        output = dto.removeAttribute(attrib3.getName());
        output2 = dto.getAttributes();
        assertTrue(output);
        assertEquals(Arrays.asList(), output2);
    }

    @Test
    public void testGetSetProductContent() {
        ProductData dto = new ProductData();
        ContentData[] content = new ContentData[] {
            new ContentData("c1", "content-1", "test_type", "test_label-1", "test_vendor-1"),
            new ContentData("c2", "content-2", "test_type", "test_label-2", "test_vendor-2"),
            new ContentData("c3", "content-3", "test_type", "test_label-3", "test_vendor-3"),
        };

        Collection<ProductContentData> input = Arrays.asList(
            new ProductContentData(content[0], true),
            new ProductContentData(content[1], false),
            new ProductContentData(content[2], true)
        );

        Collection<ProductContentData> input2 = Arrays.asList(
            new ProductContentData(content[0], false),
            new ProductContentData(content[0], true),
            new ProductContentData(content[1], true),
            new ProductContentData(content[1], false),
            new ProductContentData(content[2], false),
            new ProductContentData(content[2], true)
        );

        Collection<ProductContentData> output = dto.getProductContent();
        assertNull(output);

        ProductData output2 = dto.setProductContent(input);
        assertSame(dto, output2);

        output = dto.getProductContent();
        assertTrue(Util.collectionsAreEqual(input, output));

        // Second pass to ensure setProductContent is actually clearing existing content before
        // adding new ones
        output2 = dto.setProductContent(input);
        assertSame(dto, output2);

        output = dto.getProductContent();
        assertTrue(Util.collectionsAreEqual(input, output));

        // Third pass to ensure setting duplicates doesn't allow the dupes to be retained
        output2 = dto.setProductContent(input2);
        assertSame(dto, output2);

        output = dto.getProductContent();
        assertTrue(Util.collectionsAreEqual(input, output));
    }

    @Test
    public void testGetProductContent() {
        ProductData dto = new ProductData();
        ContentData[] content = new ContentData[] {
            new ContentData("c1", "content-1", "test_type", "test_label-1", "test_vendor-1"),
            new ContentData("c2", "content-2", "test_type", "test_label-2", "test_vendor-2"),
            new ContentData("c3", "content-3", "test_type", "test_label-3", "test_vendor-3"),
        };

        ProductContentData pcdata1 = new ProductContentData(content[0], true);
        ProductContentData pcdata2 = new ProductContentData(content[1], false);
        ProductContentData pcdata3 = new ProductContentData(content[2], true);

        ProductContentData output = dto.getProductContent("c1");
        assertNull(output);

        dto.setProductContent(Arrays.asList(pcdata1, pcdata2, pcdata3));
        output = dto.getProductContent("c1");
        assertEquals(pcdata1, output);

        output = dto.getProductContent("c3");
        assertEquals(pcdata3, output);

        output = dto.getProductContent("c4");
        assertNull(output);
    }

    @Test
    public void testHasContent() {
        ProductData dto = new ProductData();
        ContentData[] content = new ContentData[] {
            new ContentData("c1", "content-1", "test_type", "test_label-1", "test_vendor-1"),
            new ContentData("c2", "content-2", "test_type", "test_label-2", "test_vendor-2"),
            new ContentData("c3", "content-3", "test_type", "test_label-3", "test_vendor-3"),
        };

        Collection<ProductContentData> input = Arrays.asList(
            new ProductContentData(content[0], true),
            new ProductContentData(content[1], false),
            new ProductContentData(content[2], true)
        );

        boolean output = dto.hasContent("c1");
        assertFalse(output);

        dto.setProductContent(input);
        output = dto.hasContent("c1");
        assertTrue(output);

        output = dto.hasContent("c3");
        assertTrue(output);

        output = dto.hasContent("c4");
        assertFalse(output);
    }

    @Test
    public void testAddProductContentByDTO() {
        ProductData dto = new ProductData();
        ContentData[] content = new ContentData[] {
            new ContentData("c1", "content-1", "test_type", "test_label-1", "test_vendor-1"),
            new ContentData("c2", "content-2", "test_type", "test_label-2", "test_vendor-2"),
        };

        ProductContentData pcdata1 = new ProductContentData(content[0], true);
        ProductContentData pcdata2 = new ProductContentData(content[1], false);
        ProductContentData pcdata3 = new ProductContentData(content[1], true);

        assertNull(dto.getProductContent());

        boolean output = dto.addProductContent(pcdata1);
        Collection<ProductContentData> output2 = dto.getProductContent();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata1), output2));

        output = dto.addProductContent(pcdata1);
        output2 = dto.getProductContent();
        assertFalse(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata1), output2));

        output = dto.addProductContent(pcdata2);
        output2 = dto.getProductContent();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata1, pcdata2), output2));

        output = dto.addProductContent(pcdata3);
        output2 = dto.getProductContent();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata1, pcdata3), output2));
    }

    @Test
    public void testAddProductContentByEntity() {
        ProductData dto = new ProductData();
        Content[] contentEntities = new Content[] {
            new Content("c1", "content-1", "test_type", "test_label-1", "test_vendor-1"),
            new Content("c2", "content-2", "test_type", "test_label-2", "test_vendor-2"),
        };

        ProductContent pcentity1 = new ProductContent(null, contentEntities[0], true);
        ProductContent pcentity2 = new ProductContent(null, contentEntities[1], false);
        ProductContent pcentity3 = new ProductContent(null, contentEntities[1], true);

        ContentData[] content = new ContentData[] {
            contentEntities[0].toDTO(),
            contentEntities[1].toDTO()
        };

        ProductContentData pcdata1 = new ProductContentData(content[0], true);
        ProductContentData pcdata2 = new ProductContentData(content[1], false);
        ProductContentData pcdata3 = new ProductContentData(content[1], true);

        assertNull(dto.getProductContent());

        boolean output = dto.addProductContent(pcentity1);
        Collection<ProductContentData> output2 = dto.getProductContent();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata1), output2));

        output = dto.addProductContent(pcentity1);
        output2 = dto.getProductContent();
        assertFalse(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata1), output2));

        output = dto.addProductContent(pcentity2);
        output2 = dto.getProductContent();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata1, pcdata2), output2));

        output = dto.addProductContent(pcentity3);
        output2 = dto.getProductContent();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata1, pcdata3), output2));
    }

    @Test
    public void testAddContentByDTO() {
        ProductData dto = new ProductData();
        ContentData[] content = new ContentData[] {
            new ContentData("c1", "content-1", "test_type", "test_label-1", "test_vendor-1"),
            new ContentData("c2", "content-2", "test_type", "test_label-2", "test_vendor-2"),
        };

        ProductContentData pcdata1 = new ProductContentData(content[0], true);
        ProductContentData pcdata2 = new ProductContentData(content[1], false);
        ProductContentData pcdata3 = new ProductContentData(content[1], true);

        assertNull(dto.getProductContent());

        boolean output = dto.addContent(pcdata1.getContent(), pcdata1.isEnabled());
        Collection<ProductContentData> output2 = dto.getProductContent();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata1), output2));

        output = dto.addContent(pcdata1.getContent(), pcdata1.isEnabled());
        output2 = dto.getProductContent();
        assertFalse(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata1), output2));

        output = dto.addContent(pcdata2.getContent(), pcdata2.isEnabled());
        output2 = dto.getProductContent();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata1, pcdata2), output2));

        output = dto.addContent(pcdata3.getContent(), pcdata3.isEnabled());
        output2 = dto.getProductContent();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata1, pcdata3), output2));
    }

    @Test
    public void testAddContentByEntity() {
        ProductData dto = new ProductData();
        Content[] contentEntities = new Content[] {
            new Content("c1", "content-1", "test_type", "test_label-1", "test_vendor-1"),
            new Content("c2", "content-2", "test_type", "test_label-2", "test_vendor-2"),
        };

        ProductContent pcentity1 = new ProductContent(null, contentEntities[0], true);
        ProductContent pcentity2 = new ProductContent(null, contentEntities[1], false);
        ProductContent pcentity3 = new ProductContent(null, contentEntities[1], true);

        ContentData[] content = new ContentData[] {
            contentEntities[0].toDTO(),
            contentEntities[1].toDTO()
        };

        ProductContentData pcdata1 = new ProductContentData(content[0], true);
        ProductContentData pcdata2 = new ProductContentData(content[1], false);
        ProductContentData pcdata3 = new ProductContentData(content[1], true);


        assertNull(dto.getProductContent());

        boolean output = dto.addContent(pcentity1.getContent(), pcentity1.isEnabled());
        Collection<ProductContentData> output2 = dto.getProductContent();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata1), output2));

        output = dto.addContent(pcentity1.getContent(), pcentity1.isEnabled());
        output2 = dto.getProductContent();
        assertFalse(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata1), output2));

        output = dto.addContent(pcentity2.getContent(), pcentity2.isEnabled());
        output2 = dto.getProductContent();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata1, pcdata2), output2));

        output = dto.addContent(pcentity3.getContent(), pcentity3.isEnabled());
        output2 = dto.getProductContent();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata1, pcdata3), output2));
    }

    @Test
    public void testRemoveContentById() {
        ProductData dto = new ProductData();
        ContentData[] content = new ContentData[] {
            new ContentData("c1", "content-1", "test_type", "test_label-1", "test_vendor-1"),
            new ContentData("c2", "content-2", "test_type", "test_label-2", "test_vendor-2"),
        };

        ProductContentData pcdata1 = new ProductContentData(content[0], true);
        ProductContentData pcdata2 = new ProductContentData(content[1], false);

        assertNull(dto.getProductContent());
        assertFalse(dto.removeContent(content[0].getId()));
        assertFalse(dto.removeContent(content[1].getId()));

        dto.setProductContent(Arrays.asList(pcdata1, pcdata2));

        boolean output = dto.removeContent(content[0].getId());
        Collection<ProductContentData> output2 = dto.getProductContent();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata2), output2));

        output = dto.removeContent(content[0].getId());
        output2 = dto.getProductContent();
        assertFalse(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata2), output2));

        // Note that the collection should not be nulled by removing the final element
        output = dto.removeContent(content[1].getId());
        output2 = dto.getProductContent();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Collections.<ProductContentData>emptyList(), output2));
    }

    @Test
    public void testRemoveContentByDTO() {
        ProductData dto = new ProductData();
        ContentData[] content = new ContentData[] {
            new ContentData("c1", "content-1", "test_type", "test_label-1", "test_vendor-1"),
            new ContentData("c2", "content-2", "test_type", "test_label-2", "test_vendor-2"),
            new ContentData("c2", "content-3", "test_type", "test_label-3", "test_vendor-3"),
        };

        ProductContentData pcdata1 = new ProductContentData(content[0], true);
        ProductContentData pcdata2 = new ProductContentData(content[1], false);

        assertNull(dto.getProductContent());
        assertFalse(dto.removeContent(content[0]));
        assertFalse(dto.removeContent(content[1]));
        assertFalse(dto.removeContent(content[2]));

        dto.setProductContent(Arrays.asList(pcdata1, pcdata2));

        boolean output = dto.removeContent(content[0]);
        Collection<ProductContentData> output2 = dto.getProductContent();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata2), output2));

        output = dto.removeContent(content[0]);
        output2 = dto.getProductContent();
        assertFalse(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata2), output2));

        // This should work because we remove by content ID, not by exact element match
        // Note that the collection should not be nulled by removing the final element
        output = dto.removeContent(content[2]);
        output2 = dto.getProductContent();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Collections.<ProductContentData>emptyList(), output2));
    }

    @Test
    public void testRemoveContentByEntity() {
        ProductData dto = new ProductData();
        ContentData[] content = new ContentData[] {
            new ContentData("c1", "content-1", "test_type", "test_label-1", "test_vendor-1"),
            new ContentData("c2", "content-2", "test_type", "test_label-2", "test_vendor-2"),
        };

        ProductContentData pcdata1 = new ProductContentData(content[0], true);
        ProductContentData pcdata2 = new ProductContentData(content[1], false);

        Content[] contentEntities = new Content[] {
            new Content("c1", "content-1", "test_type", "test_label-1", "test_vendor-1"),
            new Content("c2", "content-2", "test_type", "test_label-2", "test_vendor-2"),
            new Content("c2", "content-3", "test_type", "test_label-3", "test_vendor-3"),
        };

        assertNull(dto.getProductContent());
        assertFalse(dto.removeContent(contentEntities[0]));
        assertFalse(dto.removeContent(contentEntities[1]));
        assertFalse(dto.removeContent(contentEntities[2]));

        dto.setProductContent(Arrays.asList(pcdata1, pcdata2));

        boolean output = dto.removeContent(contentEntities[0]);
        Collection<ProductContentData> output2 = dto.getProductContent();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata2), output2));

        output = dto.removeContent(contentEntities[0]);
        output2 = dto.getProductContent();
        assertFalse(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata2), output2));

        // This should work because we remove by content ID, not by exact element match
        // Note that the collection should not be nulled by removing the final element
        output = dto.removeContent(contentEntities[2]);
        output2 = dto.getProductContent();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Collections.<ProductContentData>emptyList(), output2));
    }

    @Test
    public void testRemoveProductContentByDTO() {
        ProductData dto = new ProductData();
        ContentData[] content = new ContentData[] {
            new ContentData("c1", "content-1", "test_type", "test_label-1", "test_vendor-1"),
            new ContentData("c2", "content-2", "test_type", "test_label-2", "test_vendor-2"),
            new ContentData("c2", "content-3", "test_type", "test_label-3", "test_vendor-3"),
        };

        ProductContentData pcdata1 = new ProductContentData(content[0], true);
        ProductContentData pcdata2 = new ProductContentData(content[1], false);
        ProductContentData pcdata3 = new ProductContentData(content[2], false);

        assertNull(dto.getProductContent());
        assertFalse(dto.removeProductContent(pcdata1));
        assertFalse(dto.removeProductContent(pcdata2));
        assertFalse(dto.removeProductContent(pcdata3));

        dto.setProductContent(Arrays.asList(pcdata1, pcdata2));

        boolean output = dto.removeProductContent(pcdata1);
        Collection<ProductContentData> output2 = dto.getProductContent();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata2), output2));

        output = dto.removeProductContent(pcdata1);
        output2 = dto.getProductContent();
        assertFalse(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata2), output2));

        // This should work because we remove by content ID, not by exact element match
        // Note that the collection should not be nulled by removing the final element
        output = dto.removeProductContent(pcdata3);
        output2 = dto.getProductContent();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Collections.<ProductContentData>emptyList(), output2));
    }

    @Test
    public void testRemoveProductContentByEntity() {
        ProductData dto = new ProductData();
        ContentData[] content = new ContentData[] {
            new ContentData("c1", "content-1", "test_type", "test_label-1", "test_vendor-1"),
            new ContentData("c2", "content-2", "test_type", "test_label-2", "test_vendor-2"),
        };

        ProductContentData pcdata1 = new ProductContentData(content[0], true);
        ProductContentData pcdata2 = new ProductContentData(content[1], false);

        Content[] contentEntities = new Content[] {
            new Content("c1", "content-1", "test_type", "test_label-1", "test_vendor-1"),
            new Content("c2", "content-2", "test_type", "test_label-2", "test_vendor-2"),
            new Content("c2", "content-3", "test_type", "test_label-3", "test_vendor-3"),
        };

        ProductContent pcentity1 = new ProductContent(null, contentEntities[0], true);
        ProductContent pcentity2 = new ProductContent(null, contentEntities[1], false);
        ProductContent pcentity3 = new ProductContent(null, contentEntities[2], true);

        assertNull(dto.getProductContent());
        assertFalse(dto.removeProductContent(pcentity1));
        assertFalse(dto.removeProductContent(pcentity2));
        assertFalse(dto.removeProductContent(pcentity3));

        dto.setProductContent(Arrays.asList(pcdata1, pcdata2));

        boolean output = dto.removeProductContent(pcentity1);
        Collection<ProductContentData> output2 = dto.getProductContent();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata2), output2));

        output = dto.removeProductContent(pcentity1);
        output2 = dto.getProductContent();
        assertFalse(output);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(pcdata2), output2));

        // This should work because we remove by content ID, not by exact element match
        // Note that the collection should not be nulled by removing the final element
        output = dto.removeProductContent(pcentity3);
        output2 = dto.getProductContent();
        assertTrue(output);
        assertTrue(Util.collectionsAreEqual(Collections.<ProductContentData>emptyList(), output2));
    }

    @Test
    public void testGetSetDependentProductIds() {
        ProductData dto = new ProductData();
        Collection<String> input = Arrays.asList("1", "2", "3");

        Collection<String> output = dto.getDependentProductIds();
        assertNull(output);

        ProductData output2 = dto.setDependentProductIds(input);
        assertSame(dto, output2);

        output = dto.getDependentProductIds();
        assertTrue(Util.collectionsAreEqual(input, output));
    }

    @Test
    public void testAddDependentProductId() {
        ProductData dto = new ProductData();

        Collection<String> pids = dto.getDependentProductIds();
        assertNull(pids);

        boolean output = dto.addDependentProductId("1");
        pids = dto.getDependentProductIds();

        assertTrue(output);
        assertNotNull(pids);
        assertTrue(Util.collectionsAreEqual(Arrays.asList("1"), pids));

        output = dto.addDependentProductId("2");
        pids = dto.getDependentProductIds();

        assertTrue(output);
        assertNotNull(pids);
        assertTrue(Util.collectionsAreEqual(Arrays.asList("1", "2"), pids));

        output = dto.addDependentProductId("1");
        pids = dto.getDependentProductIds();

        assertFalse(output);
        assertNotNull(pids);
        assertTrue(Util.collectionsAreEqual(Arrays.asList("1", "2"), pids));
    }

    @Test
    public void testRemoveDependentProductId() {
        ProductData dto = new ProductData();

        Collection<String> pids = dto.getDependentProductIds();
        assertNull(pids);

        boolean output = dto.removeDependentProductId("1");
        pids = dto.getDependentProductIds();

        assertFalse(output);
        assertNull(pids);

        dto.setDependentProductIds(Arrays.asList("1", "2"));
        pids = dto.getDependentProductIds();

        assertNotNull(pids);
        assertTrue(Util.collectionsAreEqual(Arrays.asList("1", "2"), pids));

        output = dto.removeDependentProductId("1");
        pids = dto.getDependentProductIds();

        assertTrue(output);
        assertNotNull(pids);
        assertTrue(Util.collectionsAreEqual(Arrays.asList("2"), pids));

        output = dto.removeDependentProductId("3");
        pids = dto.getDependentProductIds();

        assertFalse(output);
        assertNotNull(pids);
        assertTrue(Util.collectionsAreEqual(Arrays.asList("2"), pids));

        output = dto.removeDependentProductId("2");
        pids = dto.getDependentProductIds();

        assertTrue(output);
        assertNotNull(pids);
        assertTrue(Util.collectionsAreEqual(Arrays.<String>asList(), pids));
    }


    @Test
    public void testGetSetHref() {
        ProductData dto = new ProductData();
        String input = "test_value";

        String output = dto.getHref();
        assertNull(output);

        ProductData output2 = dto.setHref(input);
        assertSame(output2, dto);

        output = dto.getHref();
        assertEquals(input, output);
    }

    @Test
    public void testGetSetLocked() {
        ProductData dto = new ProductData();
        Boolean input = Boolean.TRUE;

        Boolean output = dto.isLocked();
        assertNull(output);

        ProductData output2 = dto.setLocked(input);
        assertSame(output2, dto);

        output = dto.isLocked();
        assertEquals(input, output);
    }

    protected Object[][] getValuesForEqualityAndReplication() {
        Collection<ProductAttributeData> attributes1 = Arrays.asList(
            new ProductAttributeData("a1", "v1"),
            new ProductAttributeData("a2", "v2"),
            new ProductAttributeData("a3", "v3")
        );

        Collection<ProductAttributeData> attributes2 = Arrays.asList(
            new ProductAttributeData("a4", "v4"),
            new ProductAttributeData("a5", "v5"),
            new ProductAttributeData("a6", "v6")
        );

        ContentData[] content = new ContentData[] {
            new ContentData("c1", "content-1", "test_type", "test_label-1", "test_vendor-1"),
            new ContentData("c2", "content-2", "test_type", "test_label-2", "test_vendor-2"),
            new ContentData("c3", "content-3", "test_type", "test_label-3", "test_vendor-3"),
            new ContentData("c4", "content-4", "test_type", "test_label-4", "test_vendor-4"),
            new ContentData("c5", "content-5", "test_type", "test_label-5", "test_vendor-5"),
            new ContentData("c6", "content-6", "test_type", "test_label-6", "test_vendor-6")
        };

        Collection<ProductContentData> productContent1 = Arrays.asList(
            new ProductContentData(content[0], true),
            new ProductContentData(content[1], false),
            new ProductContentData(content[2], true)
        );

        Collection<ProductContentData> productContent2 = Arrays.asList(
            new ProductContentData(content[3], true),
            new ProductContentData(content[4], false),
            new ProductContentData(content[5], true)
        );

        return new Object[][] {
            new Object[] { "Uuid", "test_value", "alt_value" },
            new Object[] { "Id", "test_value", "alt_value" },
            new Object[] { "Name", "test_value", "alt_value" },
            new Object[] { "Multiplier", 1234L, 4567L },
            new Object[] { "Attributes", attributes1, attributes2 },
            new Object[] { "ProductContent", productContent1, productContent2 },
            new Object[] { "DependentProductIds", Arrays.asList("1", "2", "3"), Arrays.asList("4", "5") },
            // new Object[] { "Href", "test_value", null },
            new Object[] { "Locked", Boolean.TRUE, false }
        };
    }

    protected Method[] getAccessorAndMutator(String methodSuffix, Class mutatorInputClass)
        throws Exception {

        Method accessor = null;
        Method mutator = null;

        try {
            accessor = ProductData.class.getDeclaredMethod("get" + methodSuffix, null);
        }
        catch (NoSuchMethodException e) {
            accessor = ProductData.class.getDeclaredMethod("is" + methodSuffix, null);
        }

        try {
            mutator = ProductData.class.getDeclaredMethod("set" + methodSuffix, mutatorInputClass);
        }
        catch (NoSuchMethodException e) {
            if (!Collection.class.isAssignableFrom(mutatorInputClass)) {
                throw e;
            }

            mutator = ProductData.class.getDeclaredMethod("set" + methodSuffix, Collection.class);
        }

        return new Method[] { accessor, mutator };
    }

    @Test
    public void testBaseEquality() {
        ProductData lhs = new ProductData();
        ProductData rhs = new ProductData();

        assertFalse(lhs.equals(null));
        assertTrue(lhs.equals(lhs));
        assertTrue(rhs.equals(rhs));
        assertTrue(lhs.equals(rhs));
        assertTrue(rhs.equals(lhs));
    }

    @Test
    @Parameters(method = "getValuesForEqualityAndReplication")
    public void testEquality(String valueName, Object value1, Object value2) throws Exception {
        Method[] methods = this.getAccessorAndMutator(valueName, value1.getClass());
        Method accessor = methods[0];
        Method mutator = methods[1];

        ProductData lhs = new ProductData();
        ProductData rhs = new ProductData();

        mutator.invoke(lhs, value1);
        mutator.invoke(rhs, value1);

        if (value1 instanceof Collection) {
            assertTrue(Util.collectionsAreEqual(
                (Collection) accessor.invoke(lhs), (Collection) accessor.invoke(rhs)
            ));
        }
        else {
            assertEquals(accessor.invoke(lhs), accessor.invoke(rhs));
        }

        assertTrue(lhs.equals(rhs));
        assertTrue(rhs.equals(lhs));
        assertTrue(lhs.equals(lhs));
        assertTrue(rhs.equals(rhs));
        assertEquals(lhs.hashCode(), rhs.hashCode());

        mutator.invoke(rhs, value2);

        if (value2 instanceof Collection) {
            assertFalse(Util.collectionsAreEqual(
                (Collection) accessor.invoke(lhs), (Collection) accessor.invoke(rhs)
            ));
        }
        else {
            assertNotEquals(accessor.invoke(lhs), accessor.invoke(rhs));
        }

        assertFalse(lhs.equals(rhs));
        assertFalse(rhs.equals(lhs));
        assertTrue(lhs.equals(lhs));
        assertTrue(rhs.equals(rhs));
    }

    @Test
    @Parameters(method = "getValuesForEqualityAndReplication")
    public void testClone(String valueName, Object value1, Object value2) throws Exception {
        Method[] methods = this.getAccessorAndMutator(valueName, value1.getClass());
        Method accessor = methods[0];
        Method mutator = methods[1];

        ProductData base = new ProductData();

        mutator.invoke(base, value1);

        ProductData clone = (ProductData) base.clone();

        if (value1 instanceof Collection) {
            assertTrue(Util.collectionsAreEqual(
                (Collection) accessor.invoke(base, null), (Collection) accessor.invoke(clone, null)
            ));
        }
        else {
            assertEquals(accessor.invoke(base, null), accessor.invoke(clone, null));
        }

        assertEquals(base, clone);
        assertEquals(base.hashCode(), clone.hashCode());
    }

    @Test
    @Parameters(method = "getValuesForEqualityAndReplication")
    public void testPopulateWithDTO(String valueName, Object value1, Object value2) throws Exception {
        Method[] methods = this.getAccessorAndMutator(valueName, value1.getClass());
        Method accessor = methods[0];
        Method mutator = methods[1];

        ProductData base = new ProductData();
        ProductData source = new ProductData();

        mutator.invoke(source, value1);
        base.populate(source);

        // Verify only the specified field was set
        for (Method method : ProductData.class.getDeclaredMethods()) {
            if (method.getName().matches("^(get|is)\\w+") && method.getParameterTypes().length == 0) {
                Object output = method.invoke(base, null);

                if (method.getName().equals(accessor.getName())) {
                    if (value1 instanceof Collection) {
                        assertTrue(output instanceof Collection);
                        assertTrue(Util.collectionsAreEqual((Collection) value1, (Collection) output));
                    }
                    else {
                        assertEquals(value1, output);
                    }
                }
                else {
                    assertNull(output);
                }
            }
        }
    }

    protected Object[][] getValuesPopulationByEntity() {
        return new Object[][] {
            new Object[] { "Uuid", "test_value", null },
            new Object[] { "Id", "test_value", null },
            new Object[] { "Name", "test_value", null },
            new Object[] { "Multiplier", 1234L, null },
            // new Object[] { "Attributes", attributes, Arrays.asList() },
            // new Object[] { "ProductContent", productContent, Arrays.asList() },
            new Object[] { "DependentProductIds", Arrays.asList("1", "2", "3"), Arrays.asList() },
            // new Object[] { "Href", "test_value", null },
            new Object[] { "Locked", Boolean.TRUE, false }
        };
    }

    @Test
    @Parameters(method = "getValuesPopulationByEntity")
    public void testPopulateWithEntity(String valueName, Object input, Object defaultValue) throws Exception {
        Method accessor = null;
        Method mutator = null;

        try {
            accessor = ProductData.class.getDeclaredMethod("get" + valueName, null);
        }
        catch (NoSuchMethodException e) {
            accessor = ProductData.class.getDeclaredMethod("is" + valueName, null);
        }

        try {
            mutator = Product.class.getDeclaredMethod("set" + valueName, input.getClass());
        }
        catch (NoSuchMethodException e) {
            if (Collection.class.isAssignableFrom(input.getClass())) {
                mutator = Product.class.getDeclaredMethod("set" + valueName, Collection.class);
            }
            else if (Boolean.class.isAssignableFrom(input.getClass())) {
                mutator = Product.class.getDeclaredMethod("set" + valueName, boolean.class);
            }
            else {
                throw e;
            }
        }

        ProductData base = new ProductData();
        Product source = new Product();

        mutator.invoke(source, input);
        base.populate(source);

        // Verify only the specified field was set
        for (Method method : ProductData.class.getDeclaredMethods()) {
            if (method.getName().matches("^(get|is)\\w+") && method.getParameterTypes().length == 0) {
                Object output = method.invoke(base, null);

                if (method.getName().equals(accessor.getName())) {
                    if (input instanceof Collection) {
                        assertTrue(output instanceof Collection);
                        assertTrue(Util.collectionsAreEqual((Collection) input, (Collection) output));
                    }
                    else {
                        assertEquals(input, output);
                    }
                }
                else {
                    for (Object[] values : this.getValuesPopulationByEntity()) {
                        if (method.getName().endsWith((String) values[0])) {
                            if (values[2] instanceof Collection) {
                                assertTrue(output instanceof Collection);
                                assertTrue(Util.collectionsAreEqual((Collection) values[2],
                                    (Collection) output));
                            }
                            else {
                                assertEquals(values[2], output);
                            }
                        }
                    }
                }
            }
        }
    }

    // These tests are for the values which have methods that don't conform as nicely as the
    // others, so reflection gets too messy for a single test method.

    @Test
    public void testPopulateByEntityWithAttributes() {
        ProductData base = new ProductData();
        Product source = new Product();

        ProductAttribute[] attributeEntities = new ProductAttribute[] {
            new ProductAttribute("a1", "v1"),
            new ProductAttribute("a2", "v2"),
            new ProductAttribute("a3", "v3")
        };

        ProductAttributeData padata1 = attributeEntities[0].toDTO();
        ProductAttributeData padata2 = attributeEntities[1].toDTO();
        ProductAttributeData padata3 = attributeEntities[2].toDTO();

        source.setAttributes(Arrays.asList(attributeEntities[0], attributeEntities[1], attributeEntities[2]));

        // Verify base state
        assertNull(base.getUuid());
        assertNull(base.getId());
        assertNull(base.getName());
        assertNull(base.getMultiplier());
        assertNull(base.getAttributes());
        assertNull(base.getProductContent());
        assertNull(base.getDependentProductIds());
        assertNull(base.getHref());
        assertNull(base.isLocked());

        base.populate(source);

        // Verify populated state
        assertNull(base.getUuid());
        assertNull(base.getId());
        assertNull(base.getName());
        assertNull(base.getMultiplier());
        assertNull(base.getHref());

        // Note: entities are always locked or unlocked, so this can never be null following a
        // populate.
        assertFalse(base.isLocked());

        // Note: by default, entities have empty collections (NOT null, as was the case before
        // DTOs). As a result, these will never be null after a populate.
        assertNotNull(base.getProductContent());
        assertTrue(base.getProductContent().isEmpty());
        assertNotNull(base.getDependentProductIds());
        assertTrue(base.getDependentProductIds().isEmpty());

        assertNotNull(base.getAttributes());
        assertEquals(Arrays.asList(padata1, padata2, padata3), base.getAttributes());
    }

    @Test
    public void testPopulateByEntityWithContent() {
        ProductData base = new ProductData();
        Product source = new Product();

        Content[] contentEntities = new Content[] {
            new Content("c1", "content-1", "test_type", "test_label-1", "test_vendor-1"),
            new Content("c2", "content-2", "test_type", "test_label-2", "test_vendor-2"),
            new Content("c3", "content-3", "test_type", "test_label-3", "test_vendor-3"),
        };
        ProductContent pcentity1 = new ProductContent(null, contentEntities[0], true);
        ProductContent pcentity2 = new ProductContent(null, contentEntities[1], false);
        ProductContent pcentity3 = new ProductContent(null, contentEntities[2], true);

        ProductContentData pcdata1 = pcentity1.toDTO();
        ProductContentData pcdata2 = pcentity2.toDTO();
        ProductContentData pcdata3 = pcentity3.toDTO();

        source.setProductContent(Arrays.asList(pcentity1, pcentity2, pcentity3));

        // Verify base state
        assertNull(base.getUuid());
        assertNull(base.getId());
        assertNull(base.getName());
        assertNull(base.getMultiplier());
        assertNull(base.getAttributes());
        assertNull(base.getProductContent());
        assertNull(base.getDependentProductIds());
        assertNull(base.getHref());
        assertNull(base.isLocked());

        base.populate(source);

        // Verify populated state
        assertNull(base.getUuid());
        assertNull(base.getId());
        assertNull(base.getName());
        assertNull(base.getMultiplier());
        assertNull(base.getHref());

        // Note: entities are always locked or unlocked, so this can never be null following a
        // populate.
        assertFalse(base.isLocked());

        // Note: by default, entities have empty collections (NOT null, as was the case before
        // DTOs). As a result, these will never be null after a populate.
        assertNotNull(base.getAttributes());
        assertTrue(base.getAttributes().isEmpty());
        assertNotNull(base.getDependentProductIds());
        assertTrue(base.getDependentProductIds().isEmpty());

        assertNotNull(base.getProductContent());
        assertTrue(Util.collectionsAreEqual(
            Arrays.asList(pcdata1, pcdata2, pcdata3), base.getProductContent()
        ));
    }

    @Test
    public void testPopulateByEntityWithUUID() {
        ProductData base = new ProductData();
        Product source = new Product();

        String uuid = "test_uuid";

        source.setUuid(uuid);

        // Verify base state
        assertNull(base.getUuid());
        assertNull(base.getId());
        assertNull(base.getName());
        assertNull(base.getMultiplier());
        assertNull(base.getAttributes());
        assertNull(base.getProductContent());
        assertNull(base.getDependentProductIds());
        assertNull(base.getHref());
        assertNull(base.isLocked());

        base.populate(source);

        // Verify populated state
        assertNull(base.getId());
        assertNull(base.getName());
        assertNull(base.getMultiplier());

        // Note: entities are always locked or unlocked, so this can never be null following a
        // populate.
        assertFalse(base.isLocked());

        // Note: by default, entities have empty collections (NOT null, as was the case before
        // DTOs). As a result, these will never be null after a populate.
        assertNotNull(base.getAttributes());
        assertTrue(base.getAttributes().isEmpty());
        assertNotNull(base.getProductContent());
        assertTrue(base.getProductContent().isEmpty());
        assertNotNull(base.getDependentProductIds());
        assertTrue(base.getDependentProductIds().isEmpty());

        assertEquals(uuid, base.getUuid());

        // Setting the UUID is the only way to influence the HREF from an entity. This should be
        // reflected by a populate with an entity that has a UUID.
        assertEquals("/products/" + uuid, base.getHref());
    }

}
