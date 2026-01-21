/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.jackson.DynamicPropertyFilter;
import org.candlepin.model.Branding;
import org.candlepin.model.Content;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.service.model.BrandingInfo;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.ser.std.SimpleFilterProvider;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;


public class ProductDataTest {

    public static final String PRODUCT_JSON_BASE = "{" +
        "  \"created\" : \"2016-09-07T15:08:14+0000\"," +
        "  \"updated\" : \"2016-09-07T15:08:14+0000\"," +
        "  \"uuid\" : \"8a8d01cb570530d001570531389508c7\"," +
        "  \"id\" : \"dev-sku-product\"," +
        "  \"name\" : \"Development SKU Product\"," +
        "  \"multiplier\" : 1," +
        "  \"dependentProductIds\" : [ ]," +
        "  \"href\" : \"/products/8a8d01cb570530d001570531389508c7\"," +
        "  \"productContent\" : [ ]";

    private ObjectMapper mapper;

    @BeforeEach
    public void createObjects() {
        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        filterProvider.setDefaultFilter(new DynamicPropertyFilter());

        this.mapper = JsonMapper.builder()
            .filterProvider(filterProvider)
            .build();
    }

    protected static Stream<Object> getBadStringValues() {
        return Stream.of(null, "");
    }

    private Content buildContent(String id, String name, String type, String label, String vendor) {
        return new Content(id)
            .setName(name)
            .setType(type)
            .setLabel(label)
            .setVendor(vendor);
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

    @ParameterizedTest
    @MethodSource("getBadStringValues")
    public void testGetSetIdBadValues(String input) {
        ProductData dto = new ProductData();

        String output = dto.getId();
        assertNull(output);

        assertThrows(IllegalArgumentException.class, () -> dto.setId(input));
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

        Map<String, String> input = new HashMap<>();
        input.put("a1", "v1");
        input.put("a2", "v2");
        input.put("a3", "v3");

        Map<String, String> input2 = new HashMap<>();
        input2.put("a1", "old_value");
        input2.put("a1", "v1");
        input2.put("a2", "old_value");
        input2.put("a2", "v2");
        input2.put("a3", "old_value");
        input2.put("a3", "v3");

        Map<String, String> output = dto.getAttributes();
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
    public void testgetAttributeValue() {
        ProductData dto = new ProductData();
        Map<String, String> input = new HashMap<>();
        input.put("a1", "v1");
        input.put("a2", "v2");
        input.put("a3", "v3");

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
        Map<String, String> input = new HashMap<>();
        input.put("a1", "v1");
        input.put("a2", "v2");
        input.put("a3", "v3");

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
    public void testSetAttributeByValue() {
        ProductData dto = new ProductData();
        Map<String, String> input1 = new HashMap<>();
        input1.put("a1", "v1");

        Map<String, String> input2 = new HashMap<>();
        input2.put("a1", "v1");
        input2.put("a2", "v2");

        Map<String, String> input3 = new HashMap<>();
        input3.put("a1", "v1");
        input3.put("a2", "v2");
        input3.put("a3", "v3");

        assertNull(dto.getAttributes());

        ProductData output = dto.setAttribute("a1", "v1");
        Map<String, String> output2 = dto.getAttributes();
        assertSame(output, dto);
        assertEquals(output2, input1);

        output = dto.setAttribute("a1", "v1");
        output2 = dto.getAttributes();
        assertSame(output, dto);
        assertEquals(output2, input1);

        output = dto.setAttribute("a2", "v2");
        output2 = dto.getAttributes();
        assertSame(output, dto);
        assertEquals(output2, input2);

        output = dto.setAttribute("a3", "v3");
        output2 = dto.getAttributes();
        assertSame(output, dto);
        assertEquals(output2, input3);
    }

    @Test
    public void testRemoveAttribute() {
        ProductData dto = new ProductData();
        Map<String, String> input = new HashMap<>();
        input.put("a1", "v1");
        input.put("a2", "v2");

        Map<String, String> input2 = new HashMap<>();
        input2.put("a2", "v2");

        assertNull(dto.getAttributes());
        assertFalse(dto.removeAttribute("a1"));
        assertFalse(dto.removeAttribute("a2"));
        assertFalse(dto.removeAttribute("a3"));

        dto.setAttributes(input);
        assertEquals(input, dto.getAttributes());

        boolean output = dto.removeAttribute("a1");
        Map<String, String> output2 = dto.getAttributes();
        assertTrue(output);
        assertEquals(input2, output2);

        output = dto.removeAttribute("a1");
        output2 = dto.getAttributes();
        assertFalse(output);
        assertEquals(input2, output2);

        // Note that the collection should not be nulled by removing the final element
        output = dto.removeAttribute("a2");
        output2 = dto.getAttributes();
        assertTrue(output);
        assertEquals(new HashMap<String, String>(), output2);
    }

    @Test
    public void testGetSetProductContent() {
        ProductData dto = new ProductData();
        ContentData[] content = new ContentData[] {
            new ContentData("c1", "content-1", "test_type", "test_label-1", "test_vendor-1"),
            new ContentData("c2", "content-2", "test_type", "test_label-2", "test_vendor-2"),
            new ContentData("c3", "content-3", "test_type", "test_label-3", "test_vendor-3")
        };

        Collection<ProductContentData> input = Arrays.asList(
            new ProductContentData(content[0], true),
            new ProductContentData(content[1], false),
            new ProductContentData(content[2], true));

        Collection<ProductContentData> input2 = Arrays.asList(
            new ProductContentData(content[0], false),
            new ProductContentData(content[0], true),
            new ProductContentData(content[1], true),
            new ProductContentData(content[1], false),
            new ProductContentData(content[2], false),
            new ProductContentData(content[2], true));

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
            new ProductContentData(content[2], true));

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
            buildContent("c1", "content-1", "test_type", "test_label-1", "test_vendor-1"),
            buildContent("c2", "content-2", "test_type", "test_label-2", "test_vendor-2"),
        };

        ProductContent pcentity1 = new ProductContent(contentEntities[0], true);
        ProductContent pcentity2 = new ProductContent(contentEntities[1], false);
        ProductContent pcentity3 = new ProductContent(contentEntities[1], true);

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
            buildContent("c1", "content-1", "test_type", "test_label-1", "test_vendor-1"),
            buildContent("c2", "content-2", "test_type", "test_label-2", "test_vendor-2"),
        };

        ProductContent pcentity1 = new ProductContent(contentEntities[0], true);
        ProductContent pcentity2 = new ProductContent(contentEntities[1], false);
        ProductContent pcentity3 = new ProductContent(contentEntities[1], true);

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
            buildContent("c1", "content-1", "test_type", "test_label-1", "test_vendor-1"),
            buildContent("c2", "content-2", "test_type", "test_label-2", "test_vendor-2"),
            buildContent("c2", "content-3", "test_type", "test_label-3", "test_vendor-3"),
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
            buildContent("c1", "content-1", "test_type", "test_label-1", "test_vendor-1"),
            buildContent("c2", "content-2", "test_type", "test_label-2", "test_vendor-2"),
            buildContent("c2", "content-3", "test_type", "test_label-3", "test_vendor-3"),
        };

        ProductContent pcentity1 = new ProductContent(contentEntities[0], true);
        ProductContent pcentity2 = new ProductContent(contentEntities[1], false);
        ProductContent pcentity3 = new ProductContent(contentEntities[2], true);

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
    public void testGetSetBranding() {
        ProductData dto = new ProductData();
        Collection<Branding> input = Arrays.asList(
            new Branding("eng_id_1", "brand_name_1", "OS"),
            new Branding("eng_id_2", "brand_name_2", "OS"),
            new Branding("eng_id_3", "brand_name_3", "OS"));

        Collection<Branding> output = dto.getBranding();
        assertNull(output);

        ProductData output2 = dto.setBranding(input);
        assertSame(dto, output2);

        output = dto.getBranding();
        assertTrue(Util.collectionsAreEqual(input, output));
    }

    @Test
    public void testAddBranding() {
        ProductData dto = new ProductData();

        Collection<Branding> brandings = dto.getBranding();
        assertNull(brandings);

        boolean output = dto.addBranding(new Branding("eng_id_1", "brand_name_1", "OS"));
        brandings = dto.getBranding();

        assertTrue(output);
        assertNotNull(brandings);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(
            new Branding("eng_id_1", "brand_name_1", "OS")), brandings));

        output = dto.addBranding(new Branding("eng_id_2", "brand_name_2", "OS"));
        brandings = dto.getBranding();

        assertTrue(output);
        assertNotNull(brandings);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(
            new Branding("eng_id_1", "brand_name_1", "OS"),
            new Branding("eng_id_2", "brand_name_2", "OS")), brandings));

        output = dto.addBranding(new Branding("eng_id_1", "brand_name_1", "OS"));
        brandings = dto.getBranding();

        assertFalse(output);
        assertNotNull(brandings);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(
            new Branding("eng_id_1", "brand_name_1", "OS"),
            new Branding("eng_id_2", "brand_name_2", "OS")), brandings));
    }

    @Test
    public void testRemoveBranding() {
        ProductData dto = new ProductData();

        Collection<Branding> brandings = dto.getBranding();
        assertNull(brandings);

        boolean output = dto.removeBranding(new Branding("eng_id_1", "brand_name_1", "OS"));
        brandings = dto.getBranding();

        assertFalse(output);
        assertNull(brandings);

        dto.setBranding(Arrays.asList(
            new Branding("eng_id_1", "brand_name_1", "OS"),
            new Branding("eng_id_2", "brand_name_2", "OS")));
        brandings = dto.getBranding();

        assertNotNull(brandings);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(
            new Branding("eng_id_1", "brand_name_1", "OS"),
            new Branding("eng_id_2", "brand_name_2", "OS")), brandings));

        output = dto.removeBranding(new Branding("eng_id_1", "brand_name_1", "OS"));
        brandings = dto.getBranding();

        assertTrue(output);
        assertNotNull(brandings);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(
            new Branding("eng_id_2", "brand_name_2", "OS")), brandings));

        output = dto.removeBranding(new Branding("eng_id_3", "brand_name_3", "OS"));
        brandings = dto.getBranding();

        assertFalse(output);
        assertNotNull(brandings);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(
            new Branding("eng_id_2", "brand_name_2", "OS")), brandings));

        output = dto.removeBranding(new Branding("eng_id_2", "brand_name_2", "OS"));
        brandings = dto.getBranding();

        assertTrue(output);
        assertNotNull(brandings);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(), brandings));
    }

    @Test
    public void testGetSetProvidedProducts() {
        ProductData dto = new ProductData();
        Collection<ProductData> input = Arrays.asList(
            new ProductData("eng_id_1", "name_1"),
            new ProductData("eng_id_2", "name_2"));

        Collection<ProductData> output = dto.getProvidedProducts();
        assertNull(output);

        ProductData output2 = dto.setProvidedProducts(input);
        assertSame(dto, output2);

        output = dto.getProvidedProducts();
        assertTrue(Util.collectionsAreEqual(input, output));
    }

    @Test
    public void testAddProvidedProducts() {
        ProductData dto = new ProductData();
        Collection<ProductData> providedProducts = dto.getProvidedProducts();

        assertNull(providedProducts);

        boolean output = dto.addProvidedProduct(new ProductData("eng_id_1", "name_1"));
        providedProducts = dto.getProvidedProducts();

        assertTrue(output);
        assertNotNull(providedProducts);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(
            new ProductData("eng_id_1", "name_1")), providedProducts));

        output = dto.addProvidedProduct(new ProductData("eng_id_2", "name_2"));
        providedProducts = dto.getProvidedProducts();

        assertTrue(output);
        assertNotNull(providedProducts);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(
            new ProductData("eng_id_1", "name_1"),
            new ProductData("eng_id_2", "name_2")), providedProducts));

        output = dto.addProvidedProduct(new ProductData("eng_id_1", "name_1"));
        providedProducts = dto.getProvidedProducts();

        assertFalse(output);
        assertNotNull(providedProducts);
        assertTrue(Util.collectionsAreEqual(Arrays.asList(
            new ProductData("eng_id_1", "name_1"),
            new ProductData("eng_id_2", "name_2")), providedProducts));
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

    protected static Stream<Object[]> getValuesForEqualityAndReplication() {
        Map<String, String> attributes1 = new HashMap<>();
        attributes1.put("a1", "v1");
        attributes1.put("a2", "v2");
        attributes1.put("a3", "v3");

        Map<String, String> attributes2 = new HashMap<>();
        attributes2.put("a4", "v4");
        attributes2.put("a5", "v5");
        attributes2.put("a6", "v6");

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
            new ProductContentData(content[2], true));

        Collection<ProductContentData> productContent2 = Arrays.asList(
            new ProductContentData(content[3], true),
            new ProductContentData(content[4], false),
            new ProductContentData(content[5], true));

        Collection<BrandingInfo> branding1 = Arrays.asList(
            new Branding("eng_id_1", "brand_name_1", "OS"),
            new Branding("eng_id_2", "brand_name_2", "OS"),
            new Branding("eng_id_3", "brand_name_3", "OS"));

        Collection<BrandingInfo> branding2 = Arrays.asList(
            new Branding("eng_id_4", "brand_name_4", "OS"),
            new Branding("eng_id_5", "brand_name_5", "OS"),
            new Branding("eng_id_6", "brand_name_6", "OS"));

        Set<ProductData> providedProductData1 = Util.asSet(
            new ProductData("pd1", "providedProduct1"),
            new ProductData("pd2", "providedProduct2"));

        Set<ProductData> providedProductData2 = Util.asSet(
            new ProductData("pd3", "providedProduct3"),
            new ProductData("pd4", "providedProduct4"));

        return Stream.of(
            new Object[] { "Uuid", "test_value", "alt_value" },
            new Object[] { "Id", "test_value", "alt_value" },
            new Object[] { "Name", "test_value", "alt_value" },
            new Object[] { "Multiplier", 1234L, 4567L },
            new Object[] { "Attributes", attributes1, attributes2 },
            new Object[] { "ProductContent", productContent1, productContent2 },
            new Object[] { "DependentProductIds", Arrays.asList("1", "2", "3"), Arrays.asList("4", "5") },
            new Object[] { "Branding", branding1, branding2 },
            // new Object[] { "Href", "test_value", null },
            new Object[] { "ProvidedProducts", providedProductData1, providedProductData2 });
    }

    protected Method[] getAccessorAndMutator(String methodSuffix, Class mutatorInputClass)
        throws Exception {

        Method accessor = null;
        Method mutator = null;

        try {
            accessor = ProductData.class.getDeclaredMethod("get" + methodSuffix);
        }
        catch (NoSuchMethodException e) {
            accessor = ProductData.class.getDeclaredMethod("is" + methodSuffix);
        }

        try {
            mutator = ProductData.class.getDeclaredMethod("set" + methodSuffix, mutatorInputClass);
        }
        catch (NoSuchMethodException e) {
            if (Collection.class.isAssignableFrom(mutatorInputClass)) {
                mutator = ProductData.class.getDeclaredMethod("set" + methodSuffix, Collection.class);
            }
            else if (Map.class.isAssignableFrom(mutatorInputClass)) {
                mutator = ProductData.class.getDeclaredMethod("set" + methodSuffix, Map.class);
            }
            else {
                throw e;
            }
        }

        return new Method[] { accessor, mutator };
    }

    @Test
    public void testBaseEquality() {
        ProductData lhs = new ProductData();
        ProductData rhs = new ProductData();

        assertNotEquals(null, lhs);
        assertEquals(lhs, lhs);
        assertEquals(rhs, rhs);
        assertEquals(lhs, rhs);
        assertEquals(rhs, lhs);
    }

    @ParameterizedTest
    @MethodSource("getValuesForEqualityAndReplication")
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
                (Collection) accessor.invoke(lhs), (Collection) accessor.invoke(rhs)));
        }
        else {
            assertEquals(accessor.invoke(lhs), accessor.invoke(rhs));
        }

        assertEquals(lhs, rhs);
        assertEquals(rhs, lhs);
        assertEquals(lhs, lhs);
        assertEquals(rhs, rhs);
        assertEquals(lhs.hashCode(), rhs.hashCode());

        mutator.invoke(rhs, value2);

        if (value2 instanceof Collection) {
            assertFalse(Util.collectionsAreEqual(
                (Collection) accessor.invoke(lhs), (Collection) accessor.invoke(rhs)));
        }
        else {
            assertNotEquals(accessor.invoke(lhs), accessor.invoke(rhs));
        }

        assertNotEquals(lhs, rhs);
        assertNotEquals(rhs, lhs);
        assertEquals(lhs, lhs);
        assertEquals(rhs, rhs);
    }

    @ParameterizedTest
    @MethodSource("getValuesForEqualityAndReplication")
    public void testClone(String valueName, Object value1, Object value2) throws Exception {
        Method[] methods = this.getAccessorAndMutator(valueName, value1.getClass());
        Method accessor = methods[0];
        Method mutator = methods[1];

        ProductData base = new ProductData();

        mutator.invoke(base, value1);

        ProductData clone = (ProductData) base.clone();

        if (value1 instanceof Collection) {
            assertTrue(Util.collectionsAreEqual(
                (Collection) accessor.invoke(base), (Collection) accessor.invoke(clone)));
        }
        else {
            assertEquals(accessor.invoke(base), accessor.invoke(clone));
        }

        assertEquals(base, clone);
        assertEquals(base.hashCode(), clone.hashCode());
    }

    @ParameterizedTest
    @MethodSource("getValuesForEqualityAndReplication")
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
                Object output = method.invoke(base);

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

    protected static Stream<Object[]> getValuesPopulationByEntity() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("a1", "v1");
        attributes.put("a2", "v2");
        attributes.put("a3", "v3");

        return Stream.of(
            new Object[] { "Uuid", "test_value", null },
            new Object[] { "Id", "test_value", null },
            new Object[] { "Name", "test_value", null },
            new Object[] { "Multiplier", 1234L, null },
            new Object[] { "Attributes", attributes, Collections.<String, String>emptyMap() },
            // new Object[] { "ProductContent", productContent, Arrays.asList() },
            // new Object[] { "Href", "test_value", null },
            new Object[] { "DependentProductIds", Arrays.asList("1", "2", "3"), Arrays.asList() });
    }

    @ParameterizedTest
    @MethodSource("getValuesPopulationByEntity")
    public void testPopulateWithEntity(String valueName, Object input, Object defaultValue) throws Exception {
        Method accessor = null;
        Method mutator = null;

        try {
            accessor = ProductData.class.getDeclaredMethod("get" + valueName);
        }
        catch (NoSuchMethodException e) {
            accessor = ProductData.class.getDeclaredMethod("is" + valueName);
        }

        try {
            mutator = Product.class.getDeclaredMethod("set" + valueName, input.getClass());
        }
        catch (NoSuchMethodException e) {
            if (Collection.class.isAssignableFrom(input.getClass())) {
                mutator = Product.class.getDeclaredMethod("set" + valueName, Collection.class);
            }
            else if (Map.class.isAssignableFrom(input.getClass())) {
                mutator = Product.class.getDeclaredMethod("set" + valueName, Map.class);
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
                Object output = method.invoke(base);

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
                    ProductDataTest.getValuesPopulationByEntity().forEach(value -> {
                        if (method.getName().endsWith((String) value[0])) {
                            if (value[2] instanceof Collection) {
                                assertTrue(output instanceof Collection);
                                assertTrue(Util.collectionsAreEqual((Collection) value[2],
                                    (Collection) output));
                            }
                            else {
                                assertEquals(value[2], output);
                            }
                        }
                    });
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

        Map<String, String> attributes1 = new HashMap<>();
        attributes1.put("a1", "v1");
        attributes1.put("a2", "v2");
        attributes1.put("a3", "v3");

        source.setAttributes(attributes1);

        // Verify base state
        assertNull(base.getUuid());
        assertNull(base.getId());
        assertNull(base.getName());
        assertNull(base.getMultiplier());
        assertNull(base.getAttributes());
        assertNull(base.getProductContent());
        assertNull(base.getDependentProductIds());
        assertNull(base.getHref());

        base.populate(source);

        // Verify populated state
        assertNull(base.getUuid());
        assertNull(base.getId());
        assertNull(base.getName());
        assertNull(base.getMultiplier());
        assertNull(base.getHref());

        // Note: by default, entities have empty collections (NOT null, as was the case before
        // DTOs). As a result, these will never be null after a populate.
        assertNotNull(base.getProductContent());
        assertTrue(base.getProductContent().isEmpty());
        assertNotNull(base.getDependentProductIds());
        assertTrue(base.getDependentProductIds().isEmpty());

        assertNotNull(base.getAttributes());
        assertEquals(attributes1, base.getAttributes());
    }

    @Test
    public void testPopulateByEntityWithContent() {
        ProductData base = new ProductData();
        Product source = new Product();

        Content[] contentEntities = new Content[] {
            buildContent("c1", "content-1", "test_type", "test_label-1", "test_vendor-1"),
            buildContent("c2", "content-2", "test_type", "test_label-2", "test_vendor-2"),
            buildContent("c3", "content-3", "test_type", "test_label-3", "test_vendor-3"),
        };
        ProductContent pcentity1 = new ProductContent(contentEntities[0], true);
        ProductContent pcentity2 = new ProductContent(contentEntities[1], false);
        ProductContent pcentity3 = new ProductContent(contentEntities[2], true);

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

        base.populate(source);

        // Verify populated state
        assertNull(base.getUuid());
        assertNull(base.getId());
        assertNull(base.getName());
        assertNull(base.getMultiplier());
        assertNull(base.getHref());

        // Note: by default, entities have empty collections (NOT null, as was the case before
        // DTOs). As a result, these will never be null after a populate.
        assertNotNull(base.getAttributes());
        assertTrue(base.getAttributes().isEmpty());
        assertNotNull(base.getDependentProductIds());
        assertTrue(base.getDependentProductIds().isEmpty());

        assertNotNull(base.getProductContent());
        assertTrue(Util.collectionsAreEqual(
            Arrays.asList(pcdata1, pcdata2, pcdata3), base.getProductContent()));
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

        base.populate(source);

        // Verify populated state
        assertNull(base.getId());
        assertNull(base.getName());
        assertNull(base.getMultiplier());

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

    @Test
    public void testProductAttributeJsonDeserializationV1() throws Exception {
        String attributes = "\"attributes\": [ " +
            "  {" +
            "    \"name\" : \"attrib-1\"," +
            "    \"value\" : \"value-1\"," +
            "    \"created\" : \"2016-09-07T15:08:14+0000\"," +
            "    \"updated\" : \"2016-09-07T15:08:14+0000\"" +
            "  }," +
            "  {" +
            "    \"name\" : \"attrib-2\"," +
            "    \"value\" : \"value-2\"," +
            "    \"created\" : \"2016-09-07T15:08:14+0000\"," +
            "    \"updated\" : \"2016-09-07T15:08:14+0000\"" +
            "  }," +
            "  {" +
            "    \"name\" : 3," +
            "    \"value\" : 3," +
            "    \"created\" : \"2016-09-07T15:08:14+0000\"," +
            "    \"updated\" : \"2016-09-07T15:08:14+0000\"" +
            "  }]";

        Map<String, String> expectedAttrib = new HashMap<>();
        expectedAttrib.put("attrib-1", "value-1");
        expectedAttrib.put("attrib-2", "value-2");
        expectedAttrib.put("3", "3");

        ProductData dto = this.mapper.readValue(
            PRODUCT_JSON_BASE + "," + attributes + "}", ProductData.class);

        assertEquals(expectedAttrib, dto.getAttributes());
    }

    @Test
    public void testProductAttributeJsonDeserializationV2() throws Exception {
        String attributes = "\"attributes\": { " +
            "  \"attrib-1\": \"value-1\"," +
            "  \"attrib-2\": \"value-2\"," +
            "  \"attrib-3\": 3" +
            "}";

        Map<String, String> expectedAttrib = new HashMap<>();
        expectedAttrib.put("attrib-1", "value-1");
        expectedAttrib.put("attrib-2", "value-2");
        expectedAttrib.put("attrib-3", "3");

        ProductData dto = this.mapper.readValue(
            PRODUCT_JSON_BASE + "," + attributes + "}", ProductData.class);

        assertEquals(expectedAttrib, dto.getAttributes());
    }

    @Test
    public void testSerializeProductAttributes() throws Exception {
        String expectedHeader = "\"attributes\":[{";
        String expectedValue1 = "\"name\":\"attrib-1\",\"value\":\"value-1\"";
        String expectedValue2 = "\"name\":\"attrib-2\",\"value\":\"value-2\"";
        String expectedValue3 = "\"name\":\"attrib-3\",\"value\":\"3\"";

        Map<String, String> attributes = new HashMap<>();
        attributes.put("attrib-1", "value-1");
        attributes.put("attrib-2", "value-2");
        attributes.put("attrib-3", "3");

        ProductData dto = new ProductData();
        dto.setAttributes(attributes);

        String output = this.mapper.writeValueAsString(dto);

        // Since the attributes are stored as a map, we can't guarantee any specific printed order.
        // To deal with this, we separate the value and each header, then verify them individually.
        assertTrue(output.contains(expectedHeader));
        assertTrue(output.contains(expectedValue1));
        assertTrue(output.contains(expectedValue2));
        assertTrue(output.contains(expectedValue3));
    }
}
