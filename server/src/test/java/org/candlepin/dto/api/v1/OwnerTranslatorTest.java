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
import org.candlepin.dto.DTOFactory;
import org.candlepin.model.Owner;

import static org.junit.Assert.*;

import junitparams.JUnitParamsRunner;

import org.junit.runner.RunWith;



/**
 * Test suite for the OwnerTranslator class
 */
@RunWith(JUnitParamsRunner.class)
public class OwnerTranslatorTest extends
    AbstractTranslatorTest<Owner, OwnerDTO, OwnerTranslator> {

    protected OwnerTranslator translator = new OwnerTranslator();

    protected UpstreamConsumerTranslatorTest upstreamConsumerTranslatorTest =
        new UpstreamConsumerTranslatorTest();

    @Override
    protected void initFactory(DTOFactory factory) {
        // Note that the OwnerTranslator instance here won't be the same as the one
        // returned by initTranslator. At the time of writing, this isn't important (as it's
        // stateless), but if that detail becomes significant in the future, this will need to
        // change.
        this.upstreamConsumerTranslatorTest.initFactory(factory);
        factory.registerTranslator(Owner.class, this.translator);
    }

    @Override
    protected OwnerTranslator initTranslator() {
        return this.translator;
    }

    @Override
    protected Owner initSourceEntity() {
        Owner parent = null;

        for (int i = 0; i < 3; ++i) {
            Owner owner = new Owner();

            owner.setId("owner_id-" + i);
            owner.setKey("owner_key-" + i);
            owner.setDisplayName("owner_name-" + i);
            owner.setParentOwner(parent);
            owner.setContentPrefix("content_prefix-" + i);
            owner.setDefaultServiceLevel("service_level-" + i);
            owner.setUpstreamConsumer(this.upstreamConsumerTranslatorTest.initSourceEntity());
            owner.setLogLevel("log_level-" + i);
            owner.setAutobindDisabled(true);
            owner.setContentAccessModeList(String.format("cam%1$d-a,cam%1$d-b,cam%1$d-c", i));
            owner.setContentAccessMode(String.format("cam%d-b", i));

            parent = owner;
        }

        return parent;
    }

    @Override
    protected OwnerDTO initDestDTO() {
        // Nothing fancy to do here.
        return new OwnerDTO();
    }

    @Override
    protected void verifyDTO(Owner source, OwnerDTO dto, boolean childrenGenerated) {
        if (source != null) {
            Owner src = (Owner) source;
            OwnerDTO dest = (OwnerDTO) dto;

            assertEquals(src.getId(), dest.getId());
            assertEquals(src.getKey(), dest.getKey());
            assertEquals(src.getDisplayName(), dest.getDisplayName());
            assertEquals(src.getContentPrefix(), dest.getContentPrefix());
            assertEquals(src.getDefaultServiceLevel(), dest.getDefaultServiceLevel());
            assertEquals(src.getLogLevel(), dest.getLogLevel());
            assertEquals(src.isAutobindDisabled(), dest.isAutobindDisabled());
            assertEquals(src.getContentAccessMode(), dest.getContentAccessMode());
            assertEquals(src.getContentAccessModeList(), dest.getContentAccessModeList());

            // Parent owner is a special case, since it's recursion-based rather than relying on a
            // factory to handle it. As such, it should always be present.
            this.verifyDTO(src.getParentOwner(), dest.getParentOwner(), childrenGenerated);

            if (childrenGenerated) {
                this.upstreamConsumerTranslatorTest
                    .verifyDTO(src.getUpstreamConsumer(), dest.getUpstreamConsumer(), true);
            }
            else {
                assertNull(dest.getUpstreamConsumer());
            }
        }
        else {
            assertNull(dto);
        }
    }
}
