/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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


package org.candlepin.dto.manifest.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Branding;

import java.util.Date;

class BrandingTranslatorTest extends AbstractTranslatorTest<Branding, BrandingDTO, BrandingTranslator> {

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, Branding.class, BrandingDTO.class);
    }

    @Override
    protected BrandingTranslator initObjectTranslator() {
        return new BrandingTranslator();
    }

    @Override
    protected Branding initSourceObject() {
        return new Branding("product-id", "branding-name", "branding-type")
            .setCreated(new Date(3000L))
            .setUpdated(new Date(4000L));
    }

    @Override
    protected BrandingDTO initDestinationObject() {
        return new BrandingDTO();
    }

    @Override
    protected void verifyOutput(Branding source, BrandingDTO destination, boolean childrenGenerated) {
        if (source == null) {
            assertNull(destination);
            return;
        }

        assertEquals(source.getId(), destination.getId());
        assertEquals(source.getProductId(), destination.getProductId());
        assertEquals(source.getName(), destination.getName());
        assertEquals(source.getType(), destination.getType());
        assertEquals(source.getCreated(), destination.getCreated());
        assertEquals(source.getUpdated(), destination.getUpdated());
    }
}
