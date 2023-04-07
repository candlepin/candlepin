/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.spec;

import static org.assertj.core.api.Assertions.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.InstanceOfAssertFactories.collection;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;

import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.PermissionBlueprintDTO;
import org.candlepin.dto.api.client.v1.RoleDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Permissions;
import org.candlepin.spec.bootstrap.data.builder.Roles;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.Test;

import java.util.List;

@SpecTest
class RoleResourceSpecTest {
    @Test
    public void shouldCreateRoles() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        RoleDTO role = adminClient.roles().createRole(Roles.ownerAll(owner));

        List<RoleDTO> actualRoles = adminClient.roles().getRoles();

        assertThat(actualRoles)
            .map(RoleDTO::getName)
            .contains(role.getName());
    }

    @Test
    public void shouldNotAllowOrgAdminsToListAllRoles() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(adminClient, owner);
        ApiClient userClient = ApiClients.basic(user);

        assertForbidden(() -> userClient.roles().createRole(Roles.ownerAll(owner)));
    }

    @Test
    public void shouldUpdateJustTheName() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        RoleDTO role = adminClient.roles().createRole(Roles.ownerAll(owner));
        String originalName = role.getName();
        String updatedName = StringUtil.random("name-");

        role.setName(updatedName);
        role = adminClient.roles().updateRole(originalName, role);

        assertThat(adminClient.roles().getRoleByName(role.getName()))
            .isNotNull()
            .returns(updatedName, RoleDTO::getName);
    }

    @Test
    public void shouldDeleteRoles() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        RoleDTO role = adminClient.roles().createRole(Roles.ownerAll(owner));
        assertThat(adminClient.roles().getRoleByName(role.getName()))
            .isNotNull()
            .returns(role.getName(), RoleDTO::getName);

        adminClient.roles().deleteRoleByName(role.getName());

        assertThat(adminClient.roles().getRoles())
            .map(RoleDTO::getName)
            .doesNotContain(role.getName());
    }

    @Test
    public void shouldAddUsersToARoleThenDeleteUserFromTheRole() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        RoleDTO role = adminClient.roles().createRole(Roles.ownerAll(owner));
        assertThat(role.getUsers()).isEmpty();

        UserDTO user = UserUtil.createUser(adminClient, owner);
        role = adminClient.roles().addUserToRole(role.getName(), user.getUsername());
        assertThat(role.getUsers()).hasSize(1);

        role = adminClient.roles().getRoleByName(role.getName());
        assertThat(role.getUsers()).hasSize(1);

        adminClient.roles().deleteUserFromRole(role.getName(), user.getUsername());
        role = adminClient.roles().createRole(Roles.ownerAll(owner));
        assertThat(role.getUsers()).isEmpty();
    }

    @Test
    public void shouldAddANewPermissionToARoleThenDeleteTheOriginalPermission() throws Exception {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        RoleDTO role = adminClient.roles().createRole(Roles.ownerAll(owner));
        PermissionBlueprintDTO originalPermission = role.getPermissions().get(0);

        PermissionBlueprintDTO newPermission = Permissions.OWNER_POOLS.readOnly(owner);
        role = adminClient.roles().addRolePermission(role.getName(), newPermission);
        assertThat(adminClient.roles().getRoleByName(role.getName()))
            .isNotNull()
            .extracting(RoleDTO::getPermissions, as(collection(PermissionBlueprintDTO.class)))
            .hasSize(2)
            .map(PermissionBlueprintDTO::getType)
            .containsExactlyInAnyOrder(newPermission.getType(), originalPermission.getType());

        adminClient.roles().removeRolePermission(role.getName(), originalPermission.getId());
        assertThat(adminClient.roles().getRoleByName(role.getName()))
            .isNotNull()
            .extracting(RoleDTO::getPermissions, as(collection(PermissionBlueprintDTO.class)))
            .singleElement()
            .returns(newPermission.getType(), PermissionBlueprintDTO::getType);
    }

}
