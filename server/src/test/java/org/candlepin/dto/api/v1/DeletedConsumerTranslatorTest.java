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
import org.candlepin.model.DeletedConsumer;

/**
 * Test suite for the DeletedConsumerTranslator class
 */
public class DeletedConsumerTranslatorTest extends
    AbstractTranslatorTest<DeletedConsumer, DeletedConsumerDTO, DeletedConsumerTranslator> {

    protected DeletedConsumerTranslator translator = new DeletedConsumerTranslator();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, DeletedConsumer.class, DeletedConsumerDTO.class);
    }

    @Override
    protected DeletedConsumerTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected DeletedConsumer initSourceObject() {
        DeletedConsumer source = new DeletedConsumer();
        source.setId("deleted-consumer-id");
        source.setConsumerUuid("deleted-consumer-uuid");
        source.setOwnerId("deleted-consumer-owner-id");
        source.setOwnerKey("deleted-consumer-owner-key");
        source.setOwnerDisplayName("deleted-consumer-owner-display-name");

        return source;
    }

    @Override
    protected DeletedConsumerDTO initDestinationObject() {
        return new DeletedConsumerDTO();
    }

    @Override
    protected void verifyOutput(DeletedConsumer source, DeletedConsumerDTO dest, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getConsumerUuid(), dest.getConsumerUuid());
            assertEquals(source.getOwnerId(), dest.getOwnerId());
            assertEquals(source.getOwnerKey(), dest.getOwnerKey());
        }
        else {
            assertNull(dest);
        }
    }
}
