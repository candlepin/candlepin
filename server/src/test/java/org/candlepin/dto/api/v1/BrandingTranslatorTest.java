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

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Branding;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Test suite for the BrandingTranslator class
 */
public class BrandingTranslatorTest extends
    AbstractTranslatorTest<Branding, BrandingDTO, BrandingTranslator> {

    protected BrandingTranslator translator = new BrandingTranslator();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, Branding.class, BrandingDTO.class);
    }

    @Override
    protected BrandingTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected Branding initSourceObject() {
        Branding source = new Branding();

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
    protected void verifyOutput(Branding source, BrandingDTO dest, boolean childrenGenerated) {
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
