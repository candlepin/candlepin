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

import static org.junit.Assert.*;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.ContentOverride;
import org.candlepin.model.ConsumerContentOverride;

import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;



/**
 * Test suite for the ContentOverrideTranslator class
 */
@RunWith(JUnitParamsRunner.class)
public class ContentOverrideTranslatorTest extends
    AbstractTranslatorTest<ContentOverride, ContentOverrideDTO, ContentOverrideTranslator> {

    @Override
    protected ContentOverrideTranslator initObjectTranslator() {
        this.translator = new ContentOverrideTranslator();
        return this.translator;
    }

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, ContentOverride.class, ContentOverrideDTO.class);
    }

    @Override
    protected ContentOverride initSourceObject() {
        // Any subclass is acceptable here, as we're interested in testing the translation, not the
        // override itself
        ContentOverride source = new ConsumerContentOverride();

        source.setContentLabel("test_content_label");
        source.setName("test_name");
        source.setValue("test_value");

        return source;
    }

    @Override
    protected ContentOverrideDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new ContentOverrideDTO();
    }

    @Override
    protected void verifyOutput(ContentOverride source, ContentOverrideDTO dto, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getContentLabel(), dto.getContentLabel());
            assertEquals(source.getName(), dto.getName());
            assertEquals(source.getValue(), dto.getValue());
        }
        else {
            assertNull(dto);
        }
    }
}
