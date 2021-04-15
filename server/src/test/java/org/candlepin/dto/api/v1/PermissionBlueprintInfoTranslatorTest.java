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
import org.candlepin.model.Owner;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.service.model.PermissionBlueprintInfo;



/**
 * Test suite for the PermissionBlueprintInfoTranslatorTest class.
 */

public class PermissionBlueprintInfoTranslatorTest extends
    AbstractTranslatorTest<PermissionBlueprintInfo, PermissionBlueprintDTO,
    PermissionBlueprintInfoTranslator> {

    protected PermissionBlueprintInfoTranslator translator = new PermissionBlueprintInfoTranslator();

    protected OwnerInfoTranslatorTest ownerTranslatorTest = new OwnerInfoTranslatorTest();

    @Override
    protected void initModelTranslator(ModelTranslator modelTranslator) {
        this.ownerTranslatorTest.initModelTranslator(modelTranslator);

        modelTranslator.registerTranslator(this.translator, PermissionBlueprintInfo.class,
            PermissionBlueprintDTO.class);
    }

    @Override
    protected PermissionBlueprintInfoTranslator initObjectTranslator() {
        return this.translator;
    }

    @Override
    protected PermissionBlueprintInfo initSourceObject() {
        // PermissionBlueprint is a PermissionBlueprintInfo implementation
        PermissionBlueprint source = new PermissionBlueprint(
            PermissionType.OWNER, (Owner) this.ownerTranslatorTest.initSourceObject(), Access.ALL);

        source.setId("ent-id");

        return source;
    }

    @Override
    protected PermissionBlueprintDTO initDestinationObject() {
        return new PermissionBlueprintDTO();
    }

    @Override
    protected void verifyOutput(PermissionBlueprintInfo source, PermissionBlueprintDTO dest,
        boolean childrenGenerated) {

        if (source != null) {
            // Service model objects do not provide an ID
            assertNull(dest.getId());
            assertEquals(source.getTypeName(), dest.getType());
            assertEquals(source.getAccessLevel(), dest.getAccess());

            if (source.getOwner() != null && dest.getOwner() != null) {
                assertEquals(source.getOwner().getKey(), dest.getOwner().getKey());
            }
        }
        else {
            assertNull(dest);
        }
    }
}
