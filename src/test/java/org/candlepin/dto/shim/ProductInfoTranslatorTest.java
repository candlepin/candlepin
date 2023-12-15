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
package org.candlepin.dto.shim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.BrandingDTO;
import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.dto.api.server.v1.ProductContentDTO;
import org.candlepin.dto.api.server.v1.ProductDTO;
import org.candlepin.dto.api.v1.BrandingTranslator;
import org.candlepin.model.Branding;
import org.candlepin.model.Content;
import org.candlepin.model.Product;
import org.candlepin.model.dto.ProductData;
import org.candlepin.service.model.BrandingInfo;
import org.candlepin.service.model.ContentInfo;
import org.candlepin.service.model.ProductContentInfo;
import org.candlepin.service.model.ProductInfo;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

/**
 * Test suite for the UpstreamConsumerTranslator class
 */
public class ProductInfoTranslatorTest extends
    AbstractTranslatorTest<ProductInfo, ProductDTO, ProductInfoTranslator> {

    protected ContentInfoTranslator contentTranslator = new ContentInfoTranslator();
    protected ProductInfoTranslator productTranslator = new ProductInfoTranslator();
    protected BrandingTranslator brandingTranslator = new BrandingTranslator();

    protected ContentInfoTranslatorTest contentDataTranslatorTest = new ContentInfoTranslatorTest();
    protected BrandingInfoTranslatorTest brandingTranslatorTest = new BrandingInfoTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.contentTranslator, ContentInfo.class, ContentDTO.class);
        modelTranslator.registerTranslator(this.productTranslator, ProductInfo.class, ProductDTO.class);
        modelTranslator.registerTranslator(this.brandingTranslator, Branding.class, BrandingDTO.class);

        this.brandingTranslatorTest.initModelTranslator(modelTranslator);
    }

    @Override
    protected ProductInfoTranslator initObjectTranslator() {
        return this.productTranslator;
    }

    @Override
    public ProductData initSourceObject() {
        ProductData source = new ProductData();

        Map<String, String> attributes = new HashMap<>();
        attributes.put("attrib_1", "attrib_value_1");
        attributes.put("attrib_2", "attrib_value_2");
        attributes.put("attrib_3", "attrib_value_3");

        Collection<String> depProdIds = new LinkedList<>();
        depProdIds.add("dep_prod_1");
        depProdIds.add("dep_prod_2");
        depProdIds.add("dep_prod_3");

        source.setUuid(null);
        source.setId("test_id");
        source.setName("test_name");
        source.setMultiplier(10L);
        source.setAttributes(attributes);
        source.setDependentProductIds(depProdIds);

        for (int i = 0; i < 3; ++i) {
            Content content = TestUtil.createContent("content-" + i);
            content.setUuid(content.getId() + "_uuid");

            source.addContent(content, true);
        }

        Set<Branding> brandingSet = new HashSet<>();
        brandingSet.add((Branding) this.brandingTranslatorTest.initSourceObject());
        source.setBranding(brandingSet);

        for (int i = 0; i < 3; ++i) {
            Product providedProduct = TestUtil.createProduct("provided-product-" + i);
            providedProduct.setUuid(providedProduct.getId() + "_uuid");
            ProductData productData = new ProductData(providedProduct);

            source.addProvidedProduct(productData);
        }

        Product derivedProduct = TestUtil.createProduct("derived-product-1");
        ProductData derivedProductData = new ProductData(derivedProduct);
        source.setDerivedProduct(derivedProductData);

        return source;
    }

    @Override
    protected ProductDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new ProductDTO();
    }

    @Override
    public void verifyOutput(ProductInfo source, ProductDTO dto, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getId(), dto.getId());
            assertEquals(source.getName(), dto.getName());
            assertEquals(source.getMultiplier(), dto.getMultiplier());
            assertEquals(source.getAttributes(), Util.toMap(dto.getAttributes()));
            assertEquals(source.getDependentProductIds(), dto.getDependentProductIds());

            if (childrenGenerated) {
                assertNotNull(dto.getProductContent());

                for (ProductContentInfo pc : source.getProductContent()) {
                    for (ProductContentDTO pcdto : dto.getProductContent()) {
                        ContentInfo content = pc.getContent();
                        ContentDTO cdto = pcdto.getContent();

                        assertNotNull(cdto);

                        if (cdto.getId().equals(content.getId())) {
                            assertEquals(pc.isEnabled(), pcdto.getEnabled());

                            // Pass the content off to the ContentTranslatorTest to verify it
                            this.contentDataTranslatorTest.verifyOutput(content, cdto, childrenGenerated);
                        }
                    }
                }

                assertNotNull(dto.getBranding());
                assertEquals(dto.getBranding().size(), source.getBranding().size());
                for (BrandingInfo brandingSource : source.getBranding()) {
                    for (BrandingDTO brandingDTO : dto.getBranding()) {

                        assertNotNull(brandingDTO);
                        assertNotNull(brandingDTO.getProductId());

                        if (brandingDTO.getProductId().equals(brandingSource.getProductId())) {
                            this.brandingTranslatorTest.verifyOutput(brandingSource, brandingDTO, true);
                        }
                    }
                }
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
}
