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
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertConflict;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertForbidden;
import static org.candlepin.spec.bootstrap.assertions.StatusCodeAssertions.assertNotFound;

import org.candlepin.dto.api.client.v1.OwnerDTO;
import org.candlepin.dto.api.client.v1.RoleDTO;
import org.candlepin.dto.api.client.v1.UserDTO;
import org.candlepin.spec.bootstrap.client.ApiClient;
import org.candlepin.spec.bootstrap.client.ApiClients;
import org.candlepin.spec.bootstrap.client.SpecTest;
import org.candlepin.spec.bootstrap.data.builder.Owners;
import org.candlepin.spec.bootstrap.data.builder.Roles;
import org.candlepin.spec.bootstrap.data.builder.Users;
import org.candlepin.spec.bootstrap.data.util.StringUtil;
import org.candlepin.spec.bootstrap.data.util.UserUtil;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

@SpecTest
public class UserResourceSpecTest {

    @Test
    public void shouldReturnNotFoundWhenDeletingAnUnknownUser() {
        ApiClient adminClient = ApiClients.admin();

        assertNotFound(() -> adminClient.users().deleteUser("unknown-user"));
    }

    @Test
    public void shouldReturnConflictWhenCreatingAnExistingUser() {
        ApiClient adminClient = ApiClients.admin();
        UserDTO user = adminClient.users().createUser(Users.random());

        assertConflict(() -> adminClient.users().createUser(user));
    }

    @Test
    public void shouldAllowUsersToUpdateTheirInfo() {
        ApiClient adminClient = ApiClients.admin();
        UserDTO user = adminClient.users().createUser(Users.random());
        String updatedUsername1 = StringUtil.random("username-");

        user.setUsername(updatedUsername1);
        UserDTO updatedUser = adminClient.users().createUser(user);

        assertThat(updatedUser)
            .isNotNull()
            .returns(updatedUsername1, UserDTO::getUsername)
            .extracting(UserDTO::getId)
            .isNotNull();

        String updatedUsername2 = StringUtil.random("username-");
        updatedUser.setUsername(updatedUsername2);
        updatedUser = adminClient.users().updateUser(updatedUsername1, updatedUser);

        assertThat(updatedUser)
            .isNotNull()
            .returns(updatedUsername2, UserDTO::getUsername)
            .extracting(UserDTO::getId)
            .isNotNull();
    }

    @Test
    public void shouldReturnANonEmptyListOfUsers() {
        ApiClient adminClient = ApiClients.admin();
        UserDTO user = adminClient.users().createUser(Users.random());

        List<UserDTO> actualUsers = adminClient.users().listUsers();

        assertThat(actualUsers)
            .map(UserDTO::getUsername)
            .contains(user.getUsername());
    }

    @Test
    public void shouldAllowAUserToListTheirOwners() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(adminClient, owner);
        ApiClient userClient = ApiClients.basic(user);

        List<OwnerDTO> actualOwners = userClient.users().listUserOwners(user.getUsername());

        assertThat(actualOwners)
            .singleElement()
            .isEqualTo(owner);
    }

    @Test
    public void shouldPreventAUserFromListingAnotherUsersOwners() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(adminClient, owner);
        UserDTO otherUser = UserUtil.createUser(adminClient, owner);
        ApiClient otherUserClient = ApiClients.basic(otherUser);

        assertForbidden(() -> otherUserClient.users().listUserOwners(user.getUsername()));
    }

    @Test
    public void shouldBeAbleToGetUserRoles() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());

        RoleDTO user1Role = adminClient.roles().createRole(Roles.ownerAll(owner));
        UserDTO user1 = Users.random();
        adminClient.users().createUser(user1);
        adminClient.roles().addUserToRole(user1Role.getName(), user1.getUsername());
        ApiClient user1Client = ApiClients.basic(user1);

        RoleDTO user2Role = adminClient.roles().createRole(Roles.ownerAll(owner));
        UserDTO user2 = Users.random();
        adminClient.users().createUser(user2);
        adminClient.roles().addUserToRole(user2Role.getName(), user2.getUsername());
        ApiClient user2Client = ApiClients.basic(user2);

        RoleDTO newRole = adminClient.roles().createRole(Roles.ownerReadOnly(owner));
        adminClient.roles().addUserToRole(newRole.getName(), user1.getUsername());
        List<RoleDTO> roles = user1Client.users().getUserRoles(user1.getUsername());
        assertThat(roles)
            .hasSize(2)
            .map(RoleDTO::getName)
            .contains(newRole.getName(), user1Role.getName());

        adminClient.roles().addUserToRole(newRole.getName(), user2.getUsername());
        roles = user2Client.users().getUserRoles(user2.getUsername());
        assertThat(roles)
            .hasSize(2)
            .map(RoleDTO::getName)
            .contains(newRole.getName(), user2Role.getName());

        Optional<RoleDTO> actual = roles.stream()
            .filter(role -> newRole.getName().equals(role.getName()))
            .findFirst();

        assertThat(actual).isPresent();
        assertThat(actual.get().getUsers()).isNotNull();
        actual.get().getUsers().forEach(user -> {
            assertThat(user.getUsername()).isNotEqualTo(user1.getUsername());
        });

        // admin should see both users on the role obj (not the different API call)
        assertThat(adminClient.roles().getRoleByName(newRole.getName()))
            .extracting(RoleDTO::getUsers, as(collection(UserDTO.class)))
            .map(UserDTO::getUsername)
            .contains(user1.getUsername(), user2.getUsername());
    }

    @Test
    public void shouldNotBeAbleToSeeRoleForAnotherUser() {
        ApiClient adminClient = ApiClients.admin();
        OwnerDTO owner = adminClient.owners().createOwner(Owners.random());
        UserDTO user = UserUtil.createUser(adminClient, owner);
        UserDTO otherUser = UserUtil.createUser(adminClient, owner);
        ApiClient otherUserClient = ApiClients.basic(otherUser);

        assertForbidden(() -> otherUserClient.users().getUserRoles(user.getUsername()));
    }

}
