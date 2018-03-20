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
package org.candlepin.dto.manifest.v1;

import static org.junit.Assert.*;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Consumer;

import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;

/**
 * Test suite for the ConsumerTranslator (manifest import/export) class
 */
@RunWith(JUnitParamsRunner.class)
public class ConsumerTranslatorTest extends
    AbstractTranslatorTest<Consumer, ConsumerDTO, ConsumerTranslator> {

    protected ConsumerTranslator translator = new ConsumerTranslator();

    protected ConsumerTypeTranslatorTest consumerTypeTranslatorTest = new ConsumerTypeTranslatorTest();
    protected OwnerTranslatorTest ownerTranslatorTest = new OwnerTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.consumerTypeTranslatorTest.initModelTranslator(modelTranslator);
        this.ownerTranslatorTest.initModelTranslator(modelTranslator);

        modelTranslator.registerTranslator(
            this.translator, Consumer.class, ConsumerDTO.class);
    }

    @Override
    protected ConsumerTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected Consumer initSourceObject() {
        Consumer consumer = new Consumer();

        consumer.setUuid("consumer_uuid");
        consumer.setName("consumer_name");
        consumer.setOwner(this.ownerTranslatorTest.initSourceObject());
        consumer.setContentAccessMode("test_content_access_mode");
        consumer.setType(this.consumerTypeTranslatorTest.initSourceObject());

        return consumer;
    }

    @Override
    protected ConsumerDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new ConsumerDTO();
    }

    @Override
    protected void verifyOutput(Consumer source, ConsumerDTO dest, boolean childrenGenerated) {

        if (source != null) {
            assertEquals(source.getUuid(), dest.getUuid());
            assertEquals(source.getName(), dest.getName());
            assertEquals(source.getContentAccessMode(), dest.getContentAccessMode());

            if (childrenGenerated) {
                this.ownerTranslatorTest.verifyOutput(source.getOwner(), dest.getOwner(), true);
                this.consumerTypeTranslatorTest.verifyOutput(source.getType(), dest.getType(), true);
            }
            else {
                assertNull(dest.getOwner());
                assertNull(dest.getType());
            }
        }
        else {
            assertNull(dest);
        }
    }
}
