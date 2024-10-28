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
package org.candlepin.dto.api.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.BrandingDTO;
import org.candlepin.model.Branding;
import org.candlepin.util.Util;

import java.util.Date;

/**
 * Test suite for the BrandingTranslator class
 */
public class BrandingTranslatorTest extends
    AbstractTranslatorTest<Branding, BrandingDTO, BrandingTranslator> {

    protected BrandingTranslator translator = new BrandingTranslator();

    @Override
    public void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, Branding.class, BrandingDTO.class);
    }

    @Override
    public BrandingTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    public Branding initSourceObject() {
        Date now = new Date();

        return new Branding("test-product-id", "test-name", "test-type")
            .setCreated(now)
            .setUpdated(now);
    }

    @Override
    protected BrandingDTO initDestinationObject() {
        return new BrandingDTO();
    }

    @Override
    public void verifyOutput(Branding source, BrandingDTO dest, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getProductId(), dest.getProductId());
            assertEquals(source.getName(), dest.getName());
            assertEquals(source.getType(), dest.getType());
            assertEquals(Util.toDateTime(source.getCreated()), dest.getCreated());
            assertEquals(Util.toDateTime(source.getUpdated()), dest.getUpdated());
        }
        else {
            assertNull(dest);
        }
    }
}
