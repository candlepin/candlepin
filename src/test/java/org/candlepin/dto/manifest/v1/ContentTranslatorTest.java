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
package org.candlepin.dto.manifest.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Content;

import java.util.List;



/**
 * Test suite for the ContentTranslator (manifest import/export) class
 */
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
        return new Content("test_value")
            .setUuid("test_value")
            .setMetadataExpiration(3L)
            .setType("test_value")
            .setLabel("test_value")
            .setName("test_value")
            .setVendor("test_value")
            .setContentUrl("test_value")
            .setRequiredTags("test_value")
            .setReleaseVersion("test_value")
            .setGpgUrl("test_value")
            .setModifiedProductIds(List.of("1", "2", "3"))
            .setArches("test_value");
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
            assertEquals(source.getMetadataExpiration(), dto.getMetadataExpiration());
            assertEquals(source.getId(), dto.getId());
            assertEquals(source.getType(), dto.getType());
            assertEquals(source.getLabel(), dto.getLabel());
            assertEquals(source.getName(), dto.getName());
            assertEquals(source.getVendor(), dto.getVendor());
            assertEquals(source.getContentUrl(), dto.getContentUrl());
            assertEquals(source.getRequiredTags(), dto.getRequiredTags());
            assertEquals(source.getReleaseVersion(), dto.getReleaseVersion());
            assertEquals(source.getGpgUrl(), dto.getGpgUrl());
            assertEquals(source.getModifiedProductIds(), dto.getRequiredProductIds());
            assertEquals(source.getArches(), dto.getArches());
        }
        else {
            assertNull(dto);
        }
    }
}
