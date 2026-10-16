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
import org.candlepin.model.Content;

import java.util.Date;

class ContentTranslatorTest extends AbstractTranslatorTest<Content, ContentDTO, ContentTranslator> {

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, Content.class, ContentDTO.class);
    }

    @Override
    protected ContentTranslator initObjectTranslator() {
        return new ContentTranslator();
    }

    @Override
    protected Content initSourceObject() {
        Content content = new Content("content-id")
            .setName("content-name")
            .setType("yum")
            .setLabel("content-label")
            .setVendor("vendor")
            .setContentUrl("/content")
            .setGpgUrl("/gpg")
            .setRequiredTags("tag")
            .setReleaseVersion("10")
            .setArches("x86_64");
        content.setCreated(new Date(3000L));
        content.setUpdated(new Date(4000L));
        return content;
    }

    @Override
    protected ContentDTO initDestinationObject() {
        return new ContentDTO();
    }

    @Override
    protected void verifyOutput(Content source, ContentDTO destination, boolean childrenGenerated) {
        if (source == null) {
            assertNull(destination);
            return;
        }

        assertEquals(source.getId(), destination.getId());
        assertEquals(source.getType(), destination.getType());
        assertEquals(source.getLabel(), destination.getLabel());
        assertEquals(source.getName(), destination.getName());
        assertEquals(source.getVendor(), destination.getVendor());
        assertEquals(source.getContentUrl(), destination.getContentUrl());
        assertEquals(source.getRequiredTags(), destination.getRequiredTags());
        assertEquals(source.getReleaseVersion(), destination.getReleaseVer());
        assertEquals(source.getGpgUrl(), destination.getGpgUrl());
        assertEquals(source.getArches(), destination.getArches());
        assertEquals(source.getCreated(), destination.getCreated());
        assertEquals(source.getUpdated(), destination.getUpdated());
    }
}
