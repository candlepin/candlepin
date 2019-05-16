/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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
import org.candlepin.model.ImportUpstreamConsumer;



/**
 * Test suite for the ImportUpstreamConsumerTranslator class
 */
public class ImportUpstreamConsumerTranslatorTest extends
    AbstractTranslatorTest<ImportUpstreamConsumer, ImportUpstreamConsumerDTO,
    ImportUpstreamConsumerTranslator> {

    protected ImportUpstreamConsumerTranslator translator = new ImportUpstreamConsumerTranslator();

    protected ConsumerTypeTranslatorTest consumerTypeTranslatorTest = new ConsumerTypeTranslatorTest();


    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.consumerTypeTranslatorTest.initModelTranslator(modelTranslator);

        modelTranslator.registerTranslator(
            this.translator, ImportUpstreamConsumer.class, ImportUpstreamConsumerDTO.class);
    }

    @Override
    protected ImportUpstreamConsumerTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected ImportUpstreamConsumer initSourceObject() {
        ImportUpstreamConsumer consumer = new ImportUpstreamConsumer();

        consumer.setId("consumer_id");
        consumer.setUuid("consumer_uuid");
        consumer.setName("consumer_name");
        consumer.setApiUrl("http://www.url.com");
        consumer.setWebUrl("http://www.url.com");
        consumer.setOwnerId("owner_id");
        consumer.setType(this.consumerTypeTranslatorTest.initSourceObject());

        return consumer;
    }

    @Override
    protected ImportUpstreamConsumerDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new ImportUpstreamConsumerDTO();
    }

    @Override
    protected void verifyOutput(ImportUpstreamConsumer source, ImportUpstreamConsumerDTO dest,
        boolean childrenGenerated) {

        if (source != null) {
            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getUuid(), dest.getUuid());
            assertEquals(source.getName(), dest.getName());
            assertEquals(source.getApiUrl(), dest.getApiUrl());
            assertEquals(source.getWebUrl(), dest.getWebUrl());
            assertEquals(source.getOwnerId(), dest.getOwnerId());

            if (childrenGenerated) {
                this.consumerTypeTranslatorTest.verifyOutput(source.getType(), dest.getConsumerType(), true);
            }
            else {
                assertNull(dest.getConsumerType());
            }
        }
        else {
            assertNull(dest);
        }
    }
}
