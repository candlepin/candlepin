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

import static org.junit.Assert.*;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.ConsumerCapability;

/**
 * Test suite for the CapabilityTranslator class
 */
public class CapabilityTranslatorTest extends
    AbstractTranslatorTest<ConsumerCapability, CapabilityDTO, CapabilityTranslator> {

    protected CapabilityTranslator capabilityTranslator = new CapabilityTranslator();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.capabilityTranslator, ConsumerCapability.class,
            CapabilityDTO.class);
    }

    @Override
    protected CapabilityTranslator initObjectTranslator() {
        return this.capabilityTranslator;
    }

    @Override
    protected ConsumerCapability initSourceObject() {
        ConsumerCapability source = new ConsumerCapability();

        source.setName("test_name");
        return source;
    }

    @Override
    protected CapabilityDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new CapabilityDTO();
    }

    @Override
    protected void verifyOutput(ConsumerCapability source, CapabilityDTO dto, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getId(), dto.getId());
            assertEquals(source.getName(), dto.getName());
        }
        else {
            assertNull(dto);
        }
    }
}
