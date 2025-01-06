/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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
import static org.junit.jupiter.api.Assertions.assertNull;

import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.server.v1.HypervisorConsumerWithGuestDTO;
import org.candlepin.model.HypervisorConsumerWithGuest;

public class HypervisorConsumerWithGuestTranslatorTest extends
    AbstractTranslatorTest<HypervisorConsumerWithGuest, HypervisorConsumerWithGuestDTO,
        HypervisorConsumerWithGuestTranslator> {

    private HypervisorConsumerWithGuestTranslator translator = new HypervisorConsumerWithGuestTranslator();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(translator, HypervisorConsumerWithGuest.class,
            HypervisorConsumerWithGuestDTO.class);
    }

    @Override
    protected HypervisorConsumerWithGuestTranslator initObjectTranslator() {
        return translator;
    }

    @Override
    protected HypervisorConsumerWithGuest initSourceObject() {
        return new HypervisorConsumerWithGuest("hypervisor-consumer-uuid",
            "hypervisor-consumer-name",
            "guest-uuid",
            "guest-id");
    }

    @Override
    protected HypervisorConsumerWithGuestDTO initDestinationObject() {
        return new HypervisorConsumerWithGuestDTO();
    }

    @Override
    protected void verifyOutput(HypervisorConsumerWithGuest source, HypervisorConsumerWithGuestDTO dest,
        boolean childrenGenerated) {
        if (source == null) {
            assertNull(source);
        }

        assertEquals(source.getHypervisorConsumerUuid(), dest.getHypervisorConsumerUuid());
        assertEquals(source.getHypervisorConsumerName(), dest.getHypervisorConsumerName());
        assertEquals(source.getGuestConsumerUuid(), dest.getGuestUuid());
        assertEquals(source.getGuestId(), dest.getGuestId());
    }

}

