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
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.*;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;

import org.junit.runner.RunWith;

import junitparams.JUnitParamsRunner;



/**
 * Test suite for the ConsumerTranslator (manifest import/export) class
 */
@RunWith(JUnitParamsRunner.class)
public class ConsumerTranslatorTest extends
    AbstractTranslatorTest<Consumer, ConsumerDTO, ConsumerTranslator> {

    protected ConsumerTypeCurator mockConsumerTypeCurator;
    protected OwnerCurator mockOwnerCurator;

    protected ConsumerTypeTranslatorTest consumerTypeTranslatorTest = new ConsumerTypeTranslatorTest();
    protected OwnerTranslatorTest ownerTranslatorTest = new OwnerTranslatorTest();

    @Override
    protected ConsumerTranslator initObjectTranslator() {
        this.consumerTypeTranslatorTest.initObjectTranslator();
        this.ownerTranslatorTest.initObjectTranslator();

        this.mockConsumerTypeCurator = mock(ConsumerTypeCurator.class);
        this.mockOwnerCurator = mock(OwnerCurator.class);

        this.translator = new ConsumerTranslator(this.mockConsumerTypeCurator, this.mockOwnerCurator);
        return this.translator;
    }

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.consumerTypeTranslatorTest.initModelTranslator(modelTranslator);
        this.ownerTranslatorTest.initModelTranslator(modelTranslator);

        modelTranslator.registerTranslator(this.translator, Consumer.class, ConsumerDTO.class);
    }

    @Override
    protected Consumer initSourceObject() {
        ConsumerType ctype = this.consumerTypeTranslatorTest.initSourceObject();
        Consumer consumer = new Consumer();

        consumer.setUuid("consumer_uuid");
        consumer.setName("consumer_name");
        Owner owner = this.ownerTranslatorTest.initSourceObject();
        when(mockOwnerCurator.findOwnerById(eq(owner.getId()))).thenReturn(owner);
        consumer.setOwner(owner);
        consumer.setContentAccessMode("test_content_access_mode");
        consumer.setType(ctype);

        when(mockConsumerTypeCurator.get(eq(ctype.getId()))).thenReturn(ctype);
        when(mockConsumerTypeCurator.getConsumerType(eq(consumer))).thenReturn(ctype);

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
                if (dest.getOwner() != null) {
                    assertEquals(source.getOwnerId(), dest.getOwner().getId());
                }
                else {
                    assertNull(source.getOwnerId());
                }

                ConsumerType ctype = this.mockConsumerTypeCurator.getConsumerType(source);
                this.consumerTypeTranslatorTest.verifyOutput(ctype, dest.getType(), true);
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
