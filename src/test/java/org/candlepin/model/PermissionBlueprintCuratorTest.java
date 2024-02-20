/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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
package org.candlepin.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.candlepin.auth.permissions.PermissionFactory.PermissionType;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;

import java.util.List;

public class PermissionBlueprintCuratorTest extends DatabaseTestFixture {

    @Test
    public void testFindByOwnerWithNullOwner() {
        List<PermissionBlueprint> actual = this.permissionBlueprintCurator.findByOwner(null);

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testFindByOwner() {
        Owner owner1 = new Owner()
            .setKey(TestUtil.randomString())
            .setDisplayName(TestUtil.randomString());
        owner1 = this.ownerCurator.create(owner1);

        Owner owner2 = new Owner()
            .setKey(TestUtil.randomString())
            .setDisplayName(TestUtil.randomString());
        owner2 = this.ownerCurator.create(owner2);

        Role role1 = this.roleCurator.create(new Role(TestUtil.randomString()));
        Role role2 = this.roleCurator.create(new Role(TestUtil.randomString()));

        PermissionBlueprint blueprint1 = new PermissionBlueprint();
        blueprint1.setOwner(owner1);
        blueprint1.setRole(role1);
        blueprint1.setType(PermissionType.OWNER);

        PermissionBlueprint blueprint2 = new PermissionBlueprint();
        blueprint2.setOwner(owner2);
        blueprint2.setRole(role2);
        blueprint2.setType(PermissionType.OWNER_POOLS);

        this.permissionBlueprintCurator.create(blueprint1);
        this.permissionBlueprintCurator.create(blueprint2);

        List<PermissionBlueprint> actual = this.permissionBlueprintCurator.findByOwner(owner1);

        assertThat(actual)
            .isNotNull()
            .singleElement()
            .isEqualTo(blueprint1);
    }

    @Test
    public void testFindByOwnerWithNoExistingBlueprint() {
        Owner owner = new Owner()
            .setKey(TestUtil.randomString())
            .setDisplayName(TestUtil.randomString());
        owner = this.ownerCurator.create(owner);

        List<PermissionBlueprint> actual = this.permissionBlueprintCurator.findByOwner(owner);

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

}

