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
package org.candlepin.auth.permissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;

import org.candlepin.auth.Access;
import org.candlepin.auth.permissions.PermissionFactory.PermissionType;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.Role;
import org.candlepin.model.User;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.Set;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class PermissionFactoryTest {

    @Mock
    private OwnerCurator ownerCurator;

    @Test
    public void testCreatePermissionsWithNullUser() {
        PermissionFactory factory = new PermissionFactory(ownerCurator);
        PermissionBlueprint blueprint = new PermissionBlueprint();
        List<PermissionBlueprint> blueprints = List.of(blueprint);

        assertThrows(IllegalArgumentException.class, () -> factory.createPermissions(null));
        assertThrows(IllegalArgumentException.class, () -> factory.createPermissions(null, blueprint));
        assertThrows(IllegalArgumentException.class, () -> factory
            .createPermissions(null, blueprints));
    }

    @Test
    public void testCreatePermissionsWithNullBlueprints() {
        PermissionFactory factory = new PermissionFactory(ownerCurator);
        User user = new User();
        PermissionBlueprint blueprint = null;
        List<PermissionBlueprint> blueprints = null;

        List<Permission> actual = factory.createPermissions(user, blueprint);
        assertThat(actual)
            .isNotNull()
            .isEmpty();

        actual = factory.createPermissions(user, blueprints);

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testCreatePermissionWithUserThatHasNoRole() {
        String ownerKey = TestUtil.randomString();
        Owner owner = new Owner()
            .setKey(ownerKey);

        User user = new User()
            .setPrimaryOwner(owner);

        List<Permission> actual = new PermissionFactory(ownerCurator)
            .createPermissions(user);

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testCreatePermissionWithPermissionThatHasNoOwner() {
        String ownerKey = TestUtil.randomString();
        Owner owner = new Owner();
        owner.setId(TestUtil.randomString());
        owner.setKey(ownerKey);

        doReturn(owner).when(ownerCurator).getByKey(ownerKey);

        PermissionBlueprint permissionWithNoOwner = new PermissionBlueprint();
        permissionWithNoOwner.setType(PermissionType.OWNER);
        permissionWithNoOwner.setAccess(Access.ALL);

        PermissionBlueprint ownerPoolBP = new PermissionBlueprint();
        ownerPoolBP.setType(PermissionType.OWNER_POOLS);
        ownerPoolBP.setAccess(Access.ALL);
        ownerPoolBP.setOwner(owner);

        Role role = new Role();
        role.setPermissions(Set.of(permissionWithNoOwner, ownerPoolBP));

        User user = new User();
        user.addRole(role);

        List<Permission> actual = new PermissionFactory(ownerCurator)
            .createPermissions(user);

        assertThat(actual)
            .isNotNull()
            .singleElement();
    }

    @Test
    public void testCreatePermissionWithUnsupportedPermissionType() {
        String ownerKey = TestUtil.randomString();
        Owner owner = new Owner()
            .setKey(ownerKey);

        PermissionBlueprint unsupportedPermission = new PermissionBlueprint();
        unsupportedPermission.setType(PermissionType.USERNAME_CONSUMERS_ENTITLEMENTS);
        unsupportedPermission.setAccess(Access.ALL);
        unsupportedPermission.setOwner(owner);

        Role role = new Role();
        role.setPermissions(Set.of(unsupportedPermission));

        User user = new User();
        user.addRole(role);
        user.setPrimaryOwner(owner);

        List<Permission> actual = new PermissionFactory(ownerCurator)
            .createPermissions(user);

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

    @Test
    public void testCreatePermissionsWithUserThatHasARole() {
        String ownerKey = TestUtil.randomString();
        Owner owner = new Owner();
        owner.setId(TestUtil.randomString());
        owner.setKey(ownerKey);

        doReturn(owner).when(ownerCurator).getByKey(ownerKey);

        PermissionBlueprint ownerBP = new PermissionBlueprint();
        ownerBP.setType(PermissionType.OWNER);
        ownerBP.setAccess(Access.ALL);
        ownerBP.setOwner(owner);

        PermissionBlueprint ownerPoolBP = new PermissionBlueprint();
        ownerPoolBP.setType(PermissionType.OWNER_POOLS);
        ownerPoolBP.setAccess(Access.ALL);
        ownerPoolBP.setOwner(owner);

        PermissionBlueprint usernameConsumerBP = new PermissionBlueprint();
        usernameConsumerBP.setType(PermissionType.USERNAME_CONSUMERS);
        usernameConsumerBP.setAccess(Access.ALL);
        usernameConsumerBP.setOwner(owner);

        PermissionBlueprint attachBP = new PermissionBlueprint();
        attachBP.setType(PermissionType.ATTACH);
        attachBP.setAccess(Access.ALL);
        attachBP.setOwner(owner);

        PermissionBlueprint ownerHypervisorBP = new PermissionBlueprint();
        ownerHypervisorBP.setType(PermissionType.OWNER_HYPERVISORS);
        ownerHypervisorBP.setAccess(Access.ALL);
        ownerHypervisorBP.setOwner(owner);

        PermissionBlueprint manageActivationKeyBP = new PermissionBlueprint();
        manageActivationKeyBP.setType(PermissionType.MANAGE_ACTIVATION_KEYS);
        manageActivationKeyBP.setAccess(Access.ALL);
        manageActivationKeyBP.setOwner(owner);

        Set<PermissionBlueprint> blueprints = Set.of(ownerBP, ownerPoolBP, usernameConsumerBP, attachBP,
            ownerHypervisorBP, manageActivationKeyBP);

        Role role = new Role();
        role.setPermissions(blueprints);

        User user = new User();
        user.addRole(role);
        user.setPrimaryOwner(owner);

        List<Permission> actual = new PermissionFactory(ownerCurator)
            .createPermissions(user);

        assertThat(actual)
            .isNotNull()
            // PermissionType.MANAGE_ACTIVATION_KEYS includes two permissions:
            // ActivationKeyPermission and OwnerActivationKeyPermission
            .hasSize(blueprints.size() + 1);
    }

    @Test
    public void testCreatePermissionsWithNoExistingOwner() {
        String ownerKey = TestUtil.randomString();
        Owner owner = new Owner();
        owner.setId(TestUtil.randomString());
        owner.setKey(ownerKey);

        PermissionBlueprint ownerBP = new PermissionBlueprint();
        ownerBP.setType(PermissionType.OWNER);
        ownerBP.setAccess(Access.ALL);
        ownerBP.setOwner(owner);

        PermissionBlueprint ownerPoolBP = new PermissionBlueprint();
        ownerPoolBP.setType(PermissionType.OWNER_POOLS);
        ownerPoolBP.setAccess(Access.ALL);
        ownerPoolBP.setOwner(owner);

        PermissionBlueprint usernameConsumerBP = new PermissionBlueprint();
        usernameConsumerBP.setType(PermissionType.USERNAME_CONSUMERS);
        usernameConsumerBP.setAccess(Access.ALL);
        usernameConsumerBP.setOwner(owner);

        PermissionBlueprint attachBP = new PermissionBlueprint();
        attachBP.setType(PermissionType.ATTACH);
        attachBP.setAccess(Access.ALL);
        attachBP.setOwner(owner);

        PermissionBlueprint ownerHypervisorBP = new PermissionBlueprint();
        ownerHypervisorBP.setType(PermissionType.OWNER_HYPERVISORS);
        ownerHypervisorBP.setAccess(Access.ALL);
        ownerHypervisorBP.setOwner(owner);

        PermissionBlueprint manageActivationKeyBP = new PermissionBlueprint();
        manageActivationKeyBP.setType(PermissionType.MANAGE_ACTIVATION_KEYS);
        manageActivationKeyBP.setAccess(Access.ALL);
        manageActivationKeyBP.setOwner(owner);

        Set<PermissionBlueprint> blueprints = Set.of(ownerBP, ownerPoolBP, usernameConsumerBP, attachBP,
            ownerHypervisorBP, manageActivationKeyBP);

        Role role = new Role();
        role.setPermissions(blueprints);

        User user = new User();
        user.addRole(role);
        user.setPrimaryOwner(owner);

        List<Permission> actual = new PermissionFactory(ownerCurator)
            .createPermissions(user);

        assertThat(actual)
            .isNotNull()
            .isEmpty();
    }

}
