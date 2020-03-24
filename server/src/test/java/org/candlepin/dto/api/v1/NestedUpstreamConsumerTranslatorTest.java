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
import org.candlepin.model.UpstreamConsumer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Test suit for NestedUpstreamConsumerTranslator.
 */
public class NestedUpstreamConsumerTranslatorTest extends
    AbstractTranslatorTest<UpstreamConsumer, NestedUpstreamConsumerDTO, NestedUpstreamConsumerTranslator> {

    protected NestedUpstreamConsumerTranslator translator = new NestedUpstreamConsumerTranslator();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator,
            UpstreamConsumer.class, NestedUpstreamConsumerDTO.class);
    }

    @Override
    protected NestedUpstreamConsumerTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected UpstreamConsumer initSourceObject() {
        UpstreamConsumer source = new UpstreamConsumer();
        source.setId("Random-Id");
        source.setName("Random-Name");
        source.setUuid("Random-UUID");
        return source;
    }

    @Override
    protected NestedUpstreamConsumerDTO initDestinationObject() {
        return new NestedUpstreamConsumerDTO();
    }

    @Override
    protected void verifyOutput(UpstreamConsumer source, NestedUpstreamConsumerDTO dest,
        boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getName(), dest.getName());
            assertEquals(source.getUuid(), dest.getUuid());
        }
        else {
            assertNull(dest);
        }
    }
}
