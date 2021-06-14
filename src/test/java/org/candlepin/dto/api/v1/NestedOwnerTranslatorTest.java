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
 * Test suite for the NestedOwnerTranslator class
 */
public class NestedOwnerTranslatorTest extends
    AbstractTranslatorTest<Owner, NestedOwnerDTO, NestedOwnerTranslator> {

    protected NestedOwnerTranslator translator = new NestedOwnerTranslator();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, Owner.class, NestedOwnerDTO.class);
    }

    @Override
    protected NestedOwnerTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected Owner initSourceObject() {
        Owner owner = new Owner();
        owner.setId("owner_id-1");
        owner.setKey("owner_key-1");
        owner.setDisplayName("owner_name-1");

        return owner;

    }

    @Override
    protected NestedOwnerDTO initDestinationObject() {
        // Nothing fancy to do here.
        return new NestedOwnerDTO();
    }

    @Override
    protected void verifyOutput(Owner source, NestedOwnerDTO dest, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getKey(), dest.getKey());
            assertEquals(source.getDisplayName(), dest.getDisplayName());
            assertEquals(source.getHref(), dest.getHref());
        }
        else {
            assertNull(dest);
        }
    }
}
