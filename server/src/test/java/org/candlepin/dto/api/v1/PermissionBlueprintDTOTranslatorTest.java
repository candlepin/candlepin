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

import org.candlepin.auth.Access;
import org.candlepin.auth.permissions.PermissionFactory;
import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.PermissionBlueprint;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Test suite for the PermissionBlueprintDTOTranslator class.
 */
public class PermissionBlueprintDTOTranslatorTest  extends
    AbstractTranslatorTest<PermissionBlueprintDTO,
    PermissionBlueprint, PermissionBlueprintDTOTranslator> {

    protected PermissionBlueprintDTOTranslator translator =
        new PermissionBlueprintDTOTranslator();

    protected NestedOwnerDTOTranslatorTest ownerTranslatorTest =
        new NestedOwnerDTOTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.ownerTranslatorTest.initModelTranslator(modelTranslator);

        modelTranslator.registerTranslator(this.translator, PermissionBlueprintDTO.class,
            PermissionBlueprint.class);
    }

    @Override
    protected PermissionBlueprintDTOTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected PermissionBlueprintDTO initSourceObject() {
        PermissionBlueprintDTO source = new PermissionBlueprintDTO()
            .id("ent-id")
            .type(PermissionFactory.PermissionType.OWNER.name())
            .owner(this.ownerTranslatorTest.initSourceObject())
            .access(Access.ALL.name());

        return source;
    }

    @Override
    protected PermissionBlueprint initDestinationObject() {
        return new PermissionBlueprint();
    }

    @Override
    protected void verifyOutput(PermissionBlueprintDTO source,
        PermissionBlueprint dest, boolean childrenGenerated) {
        if (source != null) {
            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getType(), dest.getType().name());
            assertEquals(source.getAccess(), dest.getAccess().name());

            if (childrenGenerated) {
                this.ownerTranslatorTest.verifyOutput(source.getOwner(), dest.getOwner(),
                    true);
            }
            else {
                assertNull(dest.getOwner());
            }
        }
        else {
            assertNull(dest);
        }
    }
}
