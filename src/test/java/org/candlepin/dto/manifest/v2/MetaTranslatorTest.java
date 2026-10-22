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
import org.candlepin.sync.Meta;

import java.util.Date;

class MetaTranslatorTest extends AbstractTranslatorTest<Meta, MetaDTO, MetaTranslator> {

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, Meta.class, MetaDTO.class);
    }

    @Override
    protected MetaTranslator initObjectTranslator() {
        return new MetaTranslator();
    }

    @Override
    protected Meta initSourceObject() {
        return new Meta("1.0", new Date(1000L), "principal", "https://web", "cdn");
    }

    @Override
    protected MetaDTO initDestinationObject() {
        return new MetaDTO();
    }

    @Override
    protected void verifyOutput(Meta source, MetaDTO destination, boolean childrenGenerated) {
        if (source == null) {
            assertNull(destination);
            return;
        }

        assertEquals("2", destination.getManifestVersion());
        assertEquals(source.getVersion(), destination.getVersion());
        assertEquals(source.getCreated(), destination.getCreated());
        assertEquals(source.getPrincipalName(), destination.getPrincipalName());
        assertEquals(source.getWebAppPrefix(), destination.getWebAppPrefix());
        assertEquals(source.getCdnLabel(), destination.getCdnLabel());
    }
}
