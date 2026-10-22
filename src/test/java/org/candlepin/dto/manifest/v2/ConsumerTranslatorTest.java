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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerTypeCurator;
import org.candlepin.model.Owner;

import java.util.Date;

class ConsumerTranslatorTest extends AbstractTranslatorTest<Consumer, ConsumerDTO, ConsumerTranslator> {

    private final ConsumerType consumerType = new ConsumerType("manifest").setId("type-id");
    private final ConsumerTypeCurator consumerTypeCurator = mock(ConsumerTypeCurator.class);

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, Consumer.class, ConsumerDTO.class);
        modelTranslator.registerTranslator(new ConsumerTypeTranslator(), ConsumerType.class, ConsumerTypeDTO.class);
    }

    @Override
    protected ConsumerTranslator initObjectTranslator() {
        return new ConsumerTranslator(this.consumerTypeCurator);
    }

    @Override
    protected Consumer initSourceObject() {
        Consumer consumer = new Consumer()
            .setUuid("consumer-uuid")
            .setName("consumer-name")
            .setOwner(new Owner().setId("owner-id"))
            .setType(this.consumerType)
            .setCreated(new Date(1000L))
            .setUpdated(new Date(2000L));
        when(this.consumerTypeCurator.getConsumerType(consumer)).thenReturn(this.consumerType);
        return consumer;
    }

    @Override
    protected ConsumerDTO initDestinationObject() {
        return new ConsumerDTO();
    }

    @Override
    protected void verifyOutput(Consumer source, ConsumerDTO destination, boolean childrenGenerated) {
        if (source == null) {
            assertNull(destination);
            return;
        }

        assertEquals(source.getUuid(), destination.getUuid());
        assertEquals(source.getName(), destination.getName());
        assertEquals(source.getOwnerId(), destination.getOwnerId());
        assertEquals(source.getCreated(), destination.getCreated());
        assertEquals(source.getUpdated(), destination.getUpdated());
        if (childrenGenerated) {
            assertEquals(this.consumerType.getId(), destination.getType().getId());
            assertEquals(this.consumerType.getLabel(), destination.getType().getName());
        }
        else {
            assertNull(destination.getType());
        }
    }
}
