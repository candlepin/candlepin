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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Content;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.test.TestUtil;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;



/**
 * Test suite for the ProductTranslator (manifest import/export) class
 */
public class ProductTranslatorTest extends
    AbstractTranslatorTest<Product, ProductDTO, ProductTranslator> {

    protected ContentTranslator contentTranslator = new ContentTranslator();
    protected ProductTranslator productTranslator = new ProductTranslator();

    protected ContentTranslatorTest contentTranslatorTest = new ContentTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.contentTranslator, Content.class, ContentDTO.class);
        modelTranslator.registerTranslator(this.productTranslator, Product.class, ProductDTO.class);
    }

    @Override
    protected ProductTranslator initObjectTranslator() {
        return this.productTranslator;
    }

    @Override
    protected Product initSourceObject() {
        Product source = new Product();

        Map<String, String> attributes = new HashMap<>();
        attributes.put("attrib_1", "attrib_value_1");
        attributes.put("attrib_2", "attrib_value_2");
        attributes.put("attrib_3", "attrib_value_3");

        source.setId("test_id");
        source.setName("test_name");
        source.setAttributes(attributes);
        source.setDependentProductIds(Arrays.asList("dep_prod-1", "dep_prod-2", "dep_prod-3"));
        source.setUuid("test_uuid");
        source.setMultiplier(3L);

        for (int i = 0; i < 3; ++i) {
            Content content = TestUtil.createContent("content-" + i);
            source.addContent(content, true);
        }

        IntStream.rangeClosed(1, 3)
            .forEach(i -> source.addBranding(TestUtil.createProductBranding(source)));

        source.setDerivedProduct(this.generateChildProduct(0));

        Collection<Product> provided = Arrays.asList(
            this.generateChildProduct(1),
            this.generateChildProduct(2),
            this.generateChildProduct(3));

        source.setProvidedProducts(provided);

        return source;
    }

    private Product generateChildProduct(int number) {
        Product child = new Product()
            .setId("child-" + number)
            .setName("child_name-" + number)
            .setUuid("test_uuid-" + number)
            .setMultiplier(5L)
            .setDependentProductIds(Arrays.asList("child_dep_prod-1", "child_dep_prod-2"));

        Map<String, String> attributes = new HashMap<>();
        attributes.put("child_attrib-1", "child_attrib_value-1");
        attributes.put("child_attrib-2", "child_attrib_value-2");
        attributes.put("child_attrib-3", "child_attrib_value-3");

        child.setAttributes(attributes);

        for (int i = 0; i < 3; ++i) {
            Content content = TestUtil.createContent("child_content-" + i);
            child.addContent(content, true);
        }

        IntStream.rangeClosed(1, 3)
            .forEach(i -> child.addBranding(TestUtil.createProductBranding(child)));

        return child;
    }

    @Override
    protected ProductDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new ProductDTO();
    }

    @Override
    protected void verifyOutput(Product source, ProductDTO dto, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getUuid(), dto.getUuid());
            assertEquals(source.getMultiplier(), dto.getMultiplier());
            assertEquals(source.getId(), dto.getId());
            assertEquals(source.getName(), dto.getName());
            assertEquals(source.getAttributes(), dto.getAttributes());
            assertEquals(source.getDependentProductIds(), dto.getDependentProductIds());

            if (source.getDerivedProduct() != null) {
                this.verifyOutput(source.getDerivedProduct(), dto.getDerivedProduct(), childrenGenerated);
            }
            else {
                assertNull(dto.getDerivedProduct());
            }

            if (source.getProvidedProducts() != null) {
                assertNotNull(dto.getProvidedProducts());

                Map<String, Product> srcProvidedMap = source.getProvidedProducts().stream()
                    .collect(Collectors.toMap(Product::getId, Function.identity()));

                Map<String, ProductDTO> destProvidedMap = dto.getProvidedProducts().stream()
                    .collect(Collectors.toMap(ProductDTO::getId, Function.identity()));

                assertEquals(srcProvidedMap.keySet(), destProvidedMap.keySet());

                for (String id : srcProvidedMap.keySet()) {
                    this.verifyOutput(srcProvidedMap.get(id), destProvidedMap.get(id), childrenGenerated);
                }
            }
            else {
                assertNull(dto.getProvidedProducts());
            }

            assertNotNull(dto.getProductContent());

            if (childrenGenerated) {
                for (ProductContent pc : source.getProductContent()) {
                    for (ProductDTO.ProductContentDTO pcdto : dto.getProductContent()) {
                        Content content = pc.getContent();
                        ContentDTO cdto = pcdto.getContent();

                        assertNotNull(cdto);
                        assertNotNull(cdto.getId());

                        if (cdto.getId().equals(content.getId())) {
                            assertEquals(pc.isEnabled(), pcdto.isEnabled());

                            // Pass the content off to the ContentTranslatorTest to verify it
                            this.contentTranslatorTest.verifyOutput(content, cdto, true);
                        }
                    }
                }
            }
            else {
                assertTrue(dto.getProductContent().isEmpty());
            }
        }
        else {
            assertNull(dto);
        }
    }
}
