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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.HypervisorId;

/**
 * Test suite for the HypervisorIdTranslator class
 */
public class HypervisorIdTranslatorTest extends
    AbstractTranslatorTest<HypervisorId, HypervisorIdDTO, HypervisorIdTranslator> {

    protected HypervisorIdTranslator hypervisorIdTranslator = new HypervisorIdTranslator();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.hypervisorIdTranslator, HypervisorId.class,
            HypervisorIdDTO.class);
    }

    @Override
    protected HypervisorIdTranslator initObjectTranslator() {
        return this.hypervisorIdTranslator;
    }

    @Override
    protected HypervisorId initSourceObject() {
        HypervisorId source = new HypervisorId();

        source.setId("test_id");
        source.setHypervisorId("test_hypervisor_id");
        source.setReporterId("test_reporter_id");
        return source;
    }

    @Override
    protected HypervisorIdDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new HypervisorIdDTO();
    }

    @Override
    protected void verifyOutput(HypervisorId source, HypervisorIdDTO dto, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getCreated(), dto.getCreated());
            assertEquals(source.getUpdated(), dto.getUpdated());
            assertEquals(source.getId(), dto.getId());
            assertEquals(source.getHypervisorId(), dto.getHypervisorId());
            assertEquals(source.getReporterId(), dto.getReporterId());
        }
        else {
            assertNull(dto);
        }
    }
}
