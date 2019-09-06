/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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
package org.candlepin.functional.user;

import static org.hamcrest.MatcherAssert.*;
import static org.junit.jupiter.api.Assertions.*;

import org.candlepin.client.ApiClient;
import org.candlepin.client.model.OwnerDTO;
import org.candlepin.client.model.RoleDTO;
import org.candlepin.client.model.UserCreationRequest;
import org.candlepin.client.model.UserDTO;
import org.candlepin.client.resources.RolesApi;
import org.candlepin.client.resources.UsersApi;
import org.candlepin.functional.ClientUtil;
import org.candlepin.functional.FunctionalTestCase;
import org.candlepin.functional.TestManifest;
import org.candlepin.functional.TestUtil;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.HttpClientErrorException.Conflict;
import org.springframework.web.client.HttpClientErrorException.Forbidden;
import org.springframework.web.client.HttpClientErrorException.NotFound;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Test the /owners resource
 */
@FunctionalTestCase
public class UserResourceTest {
    private static final Logger log = LoggerFactory.getLogger(UserResourceTest.class);

    @Autowired @Qualifier("adminApiClient") private ApiClient adminApiClient;
    @Autowired private ClientUtil clientUtil;
    @Autowired private TestUtil testUtil;
    @Autowired private TestManifest manifest;

    private OwnerDTO owner;

    @BeforeEach
    public void setUp() throws Exception {
        owner = testUtil.trivialOwner();
    }

    @Test
    public void raises404DeletingUnknownUser() throws Exception {
        UsersApi usersApi = new UsersApi(adminApiClient);
        assertThrows(NotFound.class, () -> usersApi.deleteUser("does_not_exist"));
    }

    @Test
    public void raises409WhenCreatingAnAlreadyExistingUser() throws Exception {
        UserCreationRequest userReq = new UserCreationRequest();
        userReq.setUsername(TestUtil.randomString("user-409"));
        userReq.setPassword(TestUtil.randomString());

        UsersApi usersApi = new UsersApi(adminApiClient);
        UserDTO user = usersApi.createUser(userReq);

        assertThrows(Conflict.class, () -> usersApi.createUser(userReq));
    }

    @Test
    public void allowsUsersToUpdateInformation() throws Exception {
        String username = TestUtil.randomString("user-allows");
        ApiClient userClient = clientUtil.newUserAndClient(username, owner.getKey());

        UsersApi usersApi = new UsersApi(userClient);
        UserDTO original = usersApi.getUserInfo(username);

        String updatedUserName = TestUtil.randomString("user-updated");

        UserDTO updated = new UserDTO();
        updated.setUsername(updatedUserName);

        updated = usersApi.updateUser(username, updated);
        /* Changing the name means we have to update the TestManfest so we don't try
         * to delete something that no longer exists.
         */
        manifest.pop(original);
        manifest.push(updated);

        assertEquals(original.getId(), updated.getId());
        assertNotEquals(original.getUsername(), updated.getUsername());
    }

    @Test
    public void returnsNonEmptyListOfUsers() throws Exception {
        UsersApi api = new UsersApi(adminApiClient);
        assertThat(api.listUsers().size(), Matchers.greaterThan(0));
    }

    @Test
    public void listsOwnersForUser() throws Exception {
        String username = TestUtil.randomString("user-lists");
        ApiClient userClient = clientUtil.newUserAndClient(username, owner.getKey());

        UsersApi usersApi = new UsersApi(userClient);
        List<OwnerDTO> users = usersApi.listUsersOwners(username);
        assertEquals(1, users.size());
        assertEquals(owner, users.get(0));
    }

    @Test
    public void preventsUserFromListingAnotherUsersOwners() throws Exception {
        String user1 = TestUtil.randomString("user1");
        ApiClient user1Client = clientUtil.newUserAndClient(user1, owner.getKey());

        String user2 = TestUtil.randomString("user2");
        OwnerDTO owner2 = testUtil.trivialOwner();
        ApiClient user2Client = clientUtil.newUserAndClient(user2, owner2.getKey());

        UsersApi usersApi = new UsersApi(user1Client);
        assertThrows(Forbidden.class, () -> usersApi.listUsersOwners(user2));
    }

    @Test
    public void retrievesUserRoles() throws Exception {
        String alice = TestUtil.randomString("alice-retrieves-roles");
        ApiClient aliceClient = clientUtil.newUserAndClient(alice, owner.getKey());
        UsersApi aliceApi = new UsersApi(aliceClient);
        List<RoleDTO> aliceUserRoles = aliceApi.getUserRoles(alice);
        assertEquals(1, aliceUserRoles.size());

        RoleDTO newRole = testUtil.createRole(owner.getKey(), "READ_ONLY");
        testUtil.addUserToRole(newRole, alice);

        // Ensure we see the new role
        aliceUserRoles = aliceApi.getUserRoles(alice);
        assertEquals(2, aliceUserRoles.size());

        String bob = TestUtil.randomString("bob-retrieves-roles");
        ApiClient bobClient = clientUtil.newUserAndClient(bob, owner.getKey());
        UsersApi bobApi = new UsersApi(bobClient);
        testUtil.addUserToRole(newRole, bob);

        List<RoleDTO> bobUserRoles = bobApi.getUserRoles(bob);
        assertEquals(2, bobUserRoles.size());
        for (RoleDTO r : bobUserRoles) {
            // Bob should not see Alice's user on his role
            assertThat(
                r.getUsers().stream().filter(u -> u.getUsername().equals(alice)).collect(Collectors.toList()),
                Matchers.is(Matchers.empty())
            );
        }

        // Admin should see both users on the role
        RolesApi adminRolesApi = new RolesApi(adminApiClient);
        RoleDTO adminViewOfNewRole = adminRolesApi.getRole(newRole.getName());
        List<String> usernames =
            adminViewOfNewRole.getUsers().stream().map(UserDTO::getUsername).collect(Collectors.toList());
        assertThat(usernames, Matchers.containsInAnyOrder(alice, bob));
    }

    @Test
    public void unableToViewRoleForAnotherUser() throws Exception {
        String alice = TestUtil.randomString("alice-roles");
        UserDTO user = testUtil.createUser(alice);
        testUtil.createAllAccessRoleForUser(owner.getKey(), user);

        String mallory = TestUtil.randomString("mallory-roles");
        ApiClient malloryClient = clientUtil.newUserAndClient(mallory, owner.getKey());
        UsersApi malloryApi = new UsersApi(malloryClient);
        assertThrows(Forbidden.class, () -> malloryApi.getUserRoles(alice));
    }
}
