/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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

import static org.junit.Assert.*;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.ProductBranding;

/**
 * Test suite for the ProductBrandingTranslator (manifest import/export) class
 */
public class ProductBrandingTranslatorTest extends
    AbstractTranslatorTest<ProductBranding, BrandingDTO, ProductBrandingTranslator> {

    protected ProductBrandingTranslator translator = new ProductBrandingTranslator();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, ProductBranding.class, BrandingDTO.class);
    }

    @Override
    protected ProductBrandingTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected ProductBranding initSourceObject() {
        ProductBranding source = new ProductBranding();

        source.setProductId("test-product-id");
        source.setName("test-name");
        source.setType("test-type");

        return source;
    }

    @Override
    protected BrandingDTO initDestinationObject() {
        return new BrandingDTO();
    }

    @Override
    protected void verifyOutput(ProductBranding source, BrandingDTO dest, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getProductId(), dest.getProductId());
            assertEquals(source.getName(), dest.getName());
            assertEquals(source.getType(), dest.getType());
        }
        else {
            assertNull(dest);
        }
    }
}
