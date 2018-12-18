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
import static org.mockito.Mockito.*;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;


/**
 * Test suite for the HypervisorConsumerTranslator class
 */
public class HypervisorConsumerTranslatorTest extends
    AbstractTranslatorTest<Consumer, HypervisorConsumerDTO, HypervisorConsumerTranslator> {

    private OwnerCurator mockOwnerCurator;
    protected OwnerTranslatorTest ownerTranslatorTest = new OwnerTranslatorTest();

    @Override
    protected HypervisorConsumerTranslator initObjectTranslator() {
        this.ownerTranslatorTest.initObjectTranslator();

        this.mockOwnerCurator = mock(OwnerCurator.class);

        this.translator = new HypervisorConsumerTranslator(this.mockOwnerCurator);

        return this.translator;
    }

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.ownerTranslatorTest.initModelTranslator(modelTranslator);

        modelTranslator.registerTranslator(this.translator, Consumer.class, HypervisorConsumerDTO.class);
    }

    @Override
    protected Consumer initSourceObject() {
        Owner owner = this.ownerTranslatorTest.initSourceObject();
        when(mockOwnerCurator.findOwnerById(eq(owner.getId()))).thenReturn(owner);

        Consumer consumer = new Consumer();

        consumer.setUuid("consumer_uuid");
        consumer.setName("consumer_name");
        consumer.setOwner(owner);

        return consumer;
    }

    @Override
    protected HypervisorConsumerDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new HypervisorConsumerDTO();
    }

    @Override
    protected void verifyOutput(Consumer source, HypervisorConsumerDTO dest, boolean childrenGenerated) {

        if (source != null) {
            assertEquals(source.getUuid(), dest.getUuid());
            assertEquals(source.getName(), dest.getName());

            if (childrenGenerated) {
                Owner sourceOwner = this.mockOwnerCurator.findOwnerById(source.getOwnerId());
                if (dest.getOwner() != null) {
                    String destOwnerKey = dest.getOwner().getKey();
                    assertEquals(sourceOwner.getKey(), destOwnerKey);
                }
                else {
                    assertNull(sourceOwner);
                }
            }
        }
        else {
            assertNull(dest);
        }
    }
}
