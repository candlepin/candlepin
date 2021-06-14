/**
 * Copyright (c) 2009 - 2020 Red Hat, Inc.
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
import org.candlepin.model.Owner;


/**
 * Test suite for the NestedOwnerDTOTranslator class.
 */
public class NestedOwnerDTOTranslatorTest extends
    AbstractTranslatorTest<NestedOwnerDTO, Owner, NestedOwnerDTOTranslator> {

    protected NestedOwnerDTOTranslator translator = new NestedOwnerDTOTranslator();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator,
            NestedOwnerDTO.class, Owner.class);
    }

    @Override
    protected NestedOwnerDTOTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected NestedOwnerDTO initSourceObject() {
        NestedOwnerDTO owner = new NestedOwnerDTO();

        owner.setId("owner_id");
        owner.setKey("owner_key");
        owner.setDisplayName("owner_name");

        return owner;
    }

    @Override
    protected Owner initDestinationObject() {
        return new Owner();
    }

    @Override
    protected void verifyOutput(NestedOwnerDTO source, Owner dest,
        boolean childrenGenerated) {

        if (source != null) {
            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getKey(), dest.getKey());
            assertEquals(source.getDisplayName(), dest.getDisplayName());
        }
        else {
            assertNull(dest);
        }
    }
}
