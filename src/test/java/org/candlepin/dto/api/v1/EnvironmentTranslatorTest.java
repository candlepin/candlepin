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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.ContentDTO;
import org.candlepin.dto.api.server.v1.EnvironmentContentDTO;
import org.candlepin.dto.api.server.v1.EnvironmentDTO;
import org.candlepin.model.Content;
import org.candlepin.model.Environment;
import org.candlepin.model.EnvironmentContent;
import org.candlepin.test.TestUtil;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;



/**
 * Test suite for the ProductTranslator class
 */
public class EnvironmentTranslatorTest extends
    AbstractTranslatorTest<Environment, EnvironmentDTO, EnvironmentTranslator> {

    protected ContentTranslator contentTranslator = new ContentTranslator();
    protected EnvironmentTranslator environmentTranslator = new EnvironmentTranslator();
    protected ContentTranslatorTest contentTranslatorTest = new ContentTranslatorTest();
    protected OwnerTranslatorTest ownerTranslatorTest = new OwnerTranslatorTest();
    protected NestedOwnerTranslatorTest nestedOwnerTranslatorTest = new NestedOwnerTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        ownerTranslatorTest.initModelTranslator(modelTranslator);
        modelTranslator.registerTranslator(this.contentTranslator, Content.class, ContentDTO.class);
        modelTranslator.registerTranslator(this.environmentTranslator,
            Environment.class,
            EnvironmentDTO.class);
    }

    @Override
    protected EnvironmentTranslator initObjectTranslator() {
        return this.environmentTranslator;
    }

    @Override
    protected Environment initSourceObject() {
        Environment source = new Environment();

        source.setId("test_id");
        source.setName("test_name");
        source.setType("custom");
        source.setDescription("test_description");
        source.setContentPrefix("/test/prefix");
        source.setOwner(ownerTranslatorTest.initSourceObject());

        for (int i = 0; i < 3; ++i) {
            Content content = TestUtil.createContent("content-" + i);
            content.setUuid(content.getId() + "_uuid");

            source.addContent(content, true);
        }

        return source;
    }

    @Override
    protected EnvironmentDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new EnvironmentDTO();
    }

    @Override
    protected void verifyOutput(Environment source, EnvironmentDTO dto, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getId(), dto.getId());
            assertEquals(source.getName(), dto.getName());
            assertEquals(source.getType(), dto.getType());
            assertEquals(source.getDescription(), dto.getDescription());
            assertEquals(source.getContentPrefix(), dto.getContentPrefix());

            if (childrenGenerated) {
                this.nestedOwnerTranslatorTest.verifyOutput(source.getOwner(), dto.getOwner(), true);

                assertNotNull(dto.getEnvironmentContent());

                Collection<EnvironmentContent> envcontent = source.getEnvironmentContent();
                Collection<EnvironmentContentDTO> envcontentDtos = dto.getEnvironmentContent();

                assertNotNull(envcontentDtos);
                assertEquals(envcontent.size(), envcontentDtos.size());

                Map<String, Boolean> ecMap = envcontent.stream().collect(
                    Collectors.toMap(EnvironmentContent::getContentId, EnvironmentContent::getEnabled));

                Map<String, Boolean> ecDtoMap = envcontentDtos.stream().collect(
                    Collectors.toMap(EnvironmentContentDTO::getContentId, EnvironmentContentDTO::getEnabled));

                assertEquals(ecMap, ecDtoMap);
            }
            else {
                assertNull(dto.getEnvironmentContent());
                assertNull(dto.getOwner());
            }
        }
        else {
            assertNull(dto);
        }
    }
}
