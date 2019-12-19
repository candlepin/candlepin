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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.ProductDTO.ProductContentDTO;
import org.candlepin.model.Branding;
import org.candlepin.model.Content;
import org.candlepin.model.Product;
import org.candlepin.model.ProductContent;
import org.candlepin.test.TestUtil;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

/**
 * Test suite for the ProductTranslator class
 */
public class ProductTranslatorTest extends
    AbstractTranslatorTest<Product, ProductDTO, ProductTranslator> {

    protected ContentTranslator contentTranslator = new ContentTranslator();
    protected ProductTranslator productTranslator = new ProductTranslator();

    protected ContentTranslatorTest contentTranslatorTest = new ContentTranslatorTest();
    protected BrandingTranslatorTest brandingTranslatorTest = new BrandingTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.contentTranslatorTest.initModelTranslator(modelTranslator);
        this.brandingTranslatorTest.initModelTranslator(modelTranslator);

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

        Collection<String> depProdIds = new LinkedList<>();
        depProdIds.add("dep_prod_1");
        depProdIds.add("dep_prod_2");
        depProdIds.add("dep_prod_3");

        source.setUuid("test_uuid");
        source.setId("test_id");
        source.setName("test_name");
        source.setMultiplier(10L);
        source.setAttributes(attributes);
        source.setDependentProductIds(depProdIds);
        source.setLocked(true);

        for (int i = 0; i < 3; ++i) {
            Content content = TestUtil.createContent("content-" + i);
            content.setUuid(content.getId() + "_uuid");

            source.addContent(content, true);
        }

        Set<Branding> brandingSet = new HashSet<>();
        brandingSet.add(this.brandingTranslatorTest.initSourceObject());
        source.setBranding(brandingSet);

        return source;
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
            assertEquals(source.getId(), dto.getId());
            assertEquals(source.getName(), dto.getName());
            assertEquals(source.getMultiplier(), dto.getMultiplier());
            assertEquals(source.getAttributes(), dto.getAttributes());
            assertEquals(source.getDependentProductIds(), dto.getDependentProductIds());
            assertEquals(source.isLocked(), dto.isLocked());
            assertEquals(source.getHref(), dto.getHref());

            assertNotNull(dto.getProductContent());

            if (childrenGenerated) {
                for (ProductContent pc : source.getProductContent()) {
                    for (ProductContentDTO pcdto : dto.getProductContent()) {
                        Content content = pc.getContent();
                        ContentDTO cdto = pcdto.getContent();

                        assertNotNull(cdto);
                        assertNotNull(cdto.getUuid());

                        if (cdto.getUuid().equals(content.getUuid())) {
                            assertEquals(pc.isEnabled(), pcdto.isEnabled());

                            // Pass the content off to the ContentTranslatorTest to verify it
                            this.contentTranslatorTest.verifyOutput(content, cdto, childrenGenerated);
                        }
                    }
                }

                for (Branding brandingSource : source.getBranding()) {
                    for (BrandingDTO brandingDTO : dto.getBranding()) {

                        assertNotNull(brandingDTO);
                        assertNotNull(brandingDTO.getProductId());

                        if (brandingDTO.getProductId().equals(brandingSource.getProductId())) {
                            this.brandingTranslatorTest.verifyOutput(brandingSource, brandingDTO, true);
                        }
                    }
                }

                Collection<Product> sourceProducts = source.getProvidedProducts();
                Set<ProductDTO> productsDTO = dto.getProvidedProducts();
                verifyProductsOutput(sourceProducts, productsDTO);
            }
            else {
                assertTrue(dto.getProductContent().isEmpty());
                assertTrue(dto.getBranding().isEmpty());
            }
        }
        else {
            assertNull(dto);
        }
    }

    /**
     * Verifies that the product's sets of products are translated properly.
     *
     * @param originalProducts
     *  The original collection of products we check against.
     *
     * @param dtoProducts
     *  The translated DTO set of products that we need to verify.
     */
    private void verifyProductsOutput(Collection<Product> originalProducts,
        Set<ProductDTO> dtoProducts) {
        for (Product productSource : originalProducts) {
            for (ProductDTO productDTO : dtoProducts) {

                assertNotNull(productDTO);
                assertNotNull(productDTO.getId());

                if (productDTO.getId().equals(productSource.getId())) {
                    assertTrue(productDTO.getName().equals(productSource.getName()));
                }
            }
        }
    }
}
