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
import org.candlepin.model.Content;

import static org.junit.Assert.*;

import junitparams.JUnitParamsRunner;

import org.junit.runner.RunWith;

import java.util.Arrays;



/**
 * Test suite for the UpstreamConsumerTranslator class
 */
@RunWith(JUnitParamsRunner.class)
public class ContentTranslatorTest extends
    AbstractTranslatorTest<Content, ContentDTO, ContentTranslator> {

    protected ContentTranslator translator = new ContentTranslator();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, Content.class, ContentDTO.class);
    }

    @Override
    protected ContentTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected Content initSourceObject() {
        Content source = new Content();

        source.setUuid("test_value");
        source.setId("test_value");
        source.setType("test_value");
        source.setLabel("test_value");
        source.setName("test_value");
        source.setVendor("test_value");
        source.setContentUrl("test_value");
        source.setRequiredTags("test_value");
        source.setReleaseVersion("test_value");
        source.setGpgUrl("test_value");
        source.setMetadataExpire(1234L);
        source.setModifiedProductIds(Arrays.asList("1", "2", "3"));
        source.setArches("test_value");
        source.setLocked(Boolean.TRUE);

        return source;
    }

    @Override
    protected ContentDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new ContentDTO();
    }

    @Override
    protected void verifyOutput(Content source, ContentDTO dto, boolean childrenGenerated) {
        if (source != null) {
            // This DTO does not have any nested objects, so we don't need to worry about the
            // childrenGenerated flag

            assertEquals(source.getUuid(), dto.getUuid());
            assertEquals(source.getId(), dto.getId());
            assertEquals(source.getType(), dto.getType());
            assertEquals(source.getLabel(), dto.getLabel());
            assertEquals(source.getName(), dto.getName());
            assertEquals(source.getVendor(), dto.getVendor());
            assertEquals(source.getContentUrl(), dto.getContentUrl());
            assertEquals(source.getRequiredTags(), dto.getRequiredTags());
            assertEquals(source.getReleaseVersion(), dto.getReleaseVersion());
            assertEquals(source.getGpgUrl(), dto.getGpgUrl());
            assertEquals(source.getMetadataExpire(), dto.getMetadataExpiration());
            assertEquals(source.getModifiedProductIds(), dto.getModifiedProductIds());
            assertEquals(source.getArches(), dto.getArches());
            assertEquals(source.isLocked(), dto.isLocked());
        }
        else {
            assertNull(dto);
        }
    }
}
