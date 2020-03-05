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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.candlepin.auth.Access;
import org.candlepin.auth.permissions.PermissionFactory.PermissionType;
import org.candlepin.dto.AbstractTranslatorTest;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.model.PermissionBlueprint;



/**
 * Test suite for the EntitlementTranslator class.
 */
public class PermissionBlueprintTranslatorTest extends
    AbstractTranslatorTest<PermissionBlueprint, PermissionBlueprintDTO, PermissionBlueprintTranslator> {

    protected PermissionBlueprintTranslator translator = new PermissionBlueprintTranslator();

    protected NestedOwnerTranslatorTest nestedOwnerTranslator = new NestedOwnerTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        modelTranslator.registerTranslator(this.translator, PermissionBlueprint.class,
            PermissionBlueprintDTO.class);

        this.nestedOwnerTranslator.initModelTranslator(modelTranslator);
    }

    @Override
    protected PermissionBlueprintTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected PermissionBlueprint initSourceObject() {
        PermissionBlueprint source = new PermissionBlueprint(
            PermissionType.OWNER, this.nestedOwnerTranslator.initSourceObject(), Access.ALL);

        source.setId("ent-id");

        return source;
    }

    @Override
    protected PermissionBlueprintDTO initDestinationObject() {
        return new PermissionBlueprintDTO();
    }

    @Override
    protected void verifyOutput(PermissionBlueprint source, PermissionBlueprintDTO dest,
        boolean childrenGenerated) {

        if (source != null) {
            assertEquals(source.getId(), dest.getId());
            assertEquals(source.getType().toString(), dest.getType());
            assertEquals(source.getAccess().toString(), dest.getAccess());

            if (childrenGenerated) {
                this.nestedOwnerTranslator.verifyOutput(source.getOwner(),
                    dest.getOwner(), false);
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
