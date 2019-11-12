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
import org.candlepin.model.Owner;
import org.candlepin.service.model.OwnerInfo;

import java.util.Date;



/**
 * Test suite for the OwnerInfoTranslator class
 */
public class OwnerInfoTranslatorTest extends
    AbstractTranslatorTest<OwnerInfo, OwnerDTO, OwnerInfoTranslator> {

    protected OwnerInfoTranslator translator = new OwnerInfoTranslator();

    protected UpstreamConsumerTranslatorTest upstreamConsumerTranslatorTest =
        new UpstreamConsumerTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.upstreamConsumerTranslatorTest.initModelTranslator(modelTranslator);

        modelTranslator.registerTranslator(this.translator, OwnerInfo.class, OwnerDTO.class);
    }

    @Override
    protected OwnerInfoTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected OwnerInfo initSourceObject() {
        // Owner is an OwnerInfo implementation
        Owner parent = null;

        for (int i = 0; i < 3; ++i) {
            Owner owner = new Owner();

            owner.setId("owner_id-" + i);
            owner.setKey("owner_key-" + i);
            owner.setDisplayName("owner_name-" + i);
            owner.setParentOwner(parent);
            owner.setContentPrefix("content_prefix-" + i);
            owner.setDefaultServiceLevel("service_level-" + i);
            owner.setUpstreamConsumer(this.upstreamConsumerTranslatorTest.initSourceObject());
            owner.setLogLevel("log_level-" + i);
            owner.setAutobindDisabled(true);
            owner.setAutobindHypervisorDisabled(true);
            owner.setContentAccessModeList(String.format("cam%1$d-a,cam%1$d-b,cam%1$d-c", i));
            owner.setContentAccessMode(String.format("cam%d-b", i));
            owner.setLastRefreshed(new Date());

            parent = owner;
        }

        return parent;
    }

    @Override
    protected OwnerDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new OwnerDTO();
    }

    @Override
    protected void verifyOutput(OwnerInfo source, OwnerDTO dest, boolean childrenGenerated) {
        if (source != null) {
            assertNull(dest.getId());
            assertEquals(source.getKey(), dest.getKey());
            assertNull(dest.getDisplayName());
            assertNull(dest.getContentPrefix());
            assertNull(dest.getDefaultServiceLevel());
            assertNull(dest.getLogLevel());
            assertNull(dest.isAutobindDisabled());
            assertNull(dest.getContentAccessMode());
            assertNull(dest.getContentAccessModeList());
            assertNull(dest.getLastRefreshed());
            assertNull(dest.getParentOwner());
            assertNull(dest.getUpstreamConsumer());
        }
        else {
            assertNull(dest);
        }
    }
}
