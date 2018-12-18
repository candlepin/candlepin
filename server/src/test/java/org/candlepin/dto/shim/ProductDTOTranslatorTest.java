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
package org.candlepin.dto.shim;

import static org.junit.Assert.*;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.manifest.v1.ContentDTO;
import org.candlepin.dto.manifest.v1.ProductDTO;
import org.candlepin.dto.manifest.v1.ProductDTO.ProductContentDTO;
import org.candlepin.model.dto.ContentData;
import org.candlepin.model.dto.ProductContentData;
import org.candlepin.model.dto.ProductData;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;



/**
 * Test suite for the ProductDTOTranslator class
 */
public class ProductDTOTranslatorTest extends
    AbstractTranslatorTest<ProductDTO, ProductData, ProductDTOTranslator> {

    protected ContentDTOTranslator contentTranslator = new ContentDTOTranslator();
    protected ProductDTOTranslator productTranslator = new ProductDTOTranslator();

    protected ContentDTOTranslatorTest contentDTOTranslatorTest = new ContentDTOTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.contentTranslator, ContentDTO.class, ContentData.class);
        modelTranslator.registerTranslator(this.productTranslator, ProductDTO.class, ProductData.class);
    }

    @Override
    protected ProductDTOTranslator initObjectTranslator() {
        return this.productTranslator;
    }

    @Override
    protected ProductDTO initSourceObject() {
        ProductDTO source = new ProductDTO();

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

        for (int i = 0; i < 3; ++i) {
            ContentDTO contentDTO = new ContentDTO();
            contentDTO.setId("content-dto-" + i);
            contentDTO.setUuid(contentDTO.getId() + "_uuid");
            ProductContentDTO pcDTO = new ProductContentDTO(contentDTO, true);

            source.addProductContent(pcDTO);
        }

        return source;
    }

    @Override
    protected ProductData initDestinationObject() {
        // Nothing fancy to do here.
        return new ProductData();
    }

    @Override
    protected void verifyOutput(ProductDTO source, ProductData dto, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getUuid(), dto.getUuid());
            assertEquals(source.getId(), dto.getId());
            assertEquals(source.getName(), dto.getName());
            assertEquals(source.getMultiplier(), dto.getMultiplier());
            assertEquals(source.getAttributes(), dto.getAttributes());
            assertEquals(source.getDependentProductIds(), dto.getDependentProductIds());

            assertNotNull(dto.getProductContent());

            if (childrenGenerated) {
                for (ProductContentDTO pcdto : source.getProductContent()) {
                    for (ProductContentData pcdata : dto.getProductContent()) {
                        ContentDTO cdto = pcdto.getContent();
                        ContentData cdata = pcdata.getContent();

                        assertNotNull(cdata);
                        assertNotNull(cdata.getUuid());

                        if (cdata.getUuid().equals(cdto.getUuid())) {
                            assertEquals(pcdto.isEnabled(), pcdata.isEnabled());

                            // Pass the content off to the ContentTranslatorTest to verify it
                            this.contentDTOTranslatorTest.verifyOutput(cdto, cdata, true);
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
