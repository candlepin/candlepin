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
package org.candlepin.resource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

import org.candlepin.auth.Access;
import org.candlepin.auth.permissions.PermissionFactory.PermissionType;
import org.candlepin.dto.api.server.v1.OwnerDTO;
import org.candlepin.dto.api.server.v1.UserDTO;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.CandlepinException;
import org.candlepin.exceptions.NotFoundException;
import org.candlepin.model.Owner;
import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.Role;
import org.candlepin.model.User;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.exception.user.UserDisabledException;
import org.candlepin.service.exception.user.UserLoginNotFoundException;
import org.candlepin.service.exception.user.UserServiceException;
import org.candlepin.service.impl.DefaultUserServiceAdapter;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;



/**
 * UserResourceTest
 */
public class UserResourceTest extends DatabaseTestFixture {

    private UserServiceAdapter userServiceAdapter;
    private UserResource userResource;

    @BeforeEach
    public void init() throws Exception {
        super.init();

        // This test suite relies upon the resource being backed by the default user service
        this.userServiceAdapter = new DefaultUserServiceAdapter(this.userCurator, this.roleCurator,
            this.permissionBlueprintCurator, this.ownerCurator, this.permissionFactory);

        this.userResource = new UserResource(this.userServiceAdapter, this.i18n, this.ownerCurator,
            this.modelTranslator);
    }

    @Test
    public void testCreateUser() {
        UserDTO dto = new UserDTO()
            .username("test-user")
            .password("banana")
            .superAdmin(true);

        User existing = this.userCurator.findByLogin(dto.getUsername());
        assertNull(existing);

        UserDTO output = this.userResource.createUser(dto);

        assertNotNull(output);
        assertEquals(dto.getUsername(), output.getUsername());
        assertEquals(dto.getSuperAdmin(), output.getSuperAdmin());

        // We better not be exposing this, ever.
        assertNull(output.getPassword());

        // Verify we actually created a user
        existing = this.userCurator.findByLogin(dto.getUsername());

        assertNotNull(existing);
        assertEquals(dto.getUsername(), existing.getUsername());
        assertEquals(Util.hash(dto.getPassword()), existing.getHashedPassword());
        assertEquals(dto.getSuperAdmin(), existing.isSuperAdmin());
    }

    @Test
    public void testCreateUserNoUsername() {
        UserDTO dto = new UserDTO()
            .password("banana")
            .superAdmin(true);

        assertThrows(BadRequestException.class, () -> this.userResource.createUser(dto));
    }

    @Test
    public void testLookupUser() {
        User user = new User();
        user.setUsername("test-user");
        user.setPassword("banana");
        user.setSuperAdmin(true);

        this.userCurator.create(user);

        UserDTO output = this.userResource.getUserInfo(user.getUsername());

        assertNotNull(output);
        assertEquals(output.getUsername(), user.getUsername());
        assertEquals(output.getSuperAdmin(), user.isSuperAdmin());

        // We better not be exposing this, ever.
        assertNull(output.getPassword());
    }

    @Test
    public void testLookupUserDoesntExist() {
        User user = new User();
        user.setUsername("test-user");
        user.setPassword("banana");
        user.setSuperAdmin(true);

        this.userCurator.create(user);

        assertThrows(NotFoundException.class, () -> this.userResource.getUserInfo("no such user"));
    }

    @Test
    public void testLookupUserNullUsername() {
        User user = new User();
        user.setUsername("test-user");
        user.setPassword("banana");
        user.setSuperAdmin(true);

        this.userCurator.create(user);

        assertThrows(BadRequestException.class, () -> this.userResource.getUserInfo(null));
    }

    // test lookup doesn't exist
    // test lookup bad input

    @Test
    public void testListAllUsers() {
        // Impl note: the DefaultUserServiceAdapter (which is backing this test suite) automatically
        // creates an admin user at build time, so we need to account for that here.
        int userCount = 5;
        for (int i = 0; i < userCount; ++i) {
            User user = new User();
            user.setUsername("test-user-" + i);
            user.setPassword("banana");
            user.setSuperAdmin(true);

            this.userCurator.create(user);
        }

        Map<String, User> existingUsers = new HashMap<>();
        for (User user : this.userCurator.listAll()) {
            assertFalse(existingUsers.containsKey(user.getUsername()));
            existingUsers.put(user.getUsername(), user);
        }

        Stream<UserDTO> response = this.userResource.listUsers();

        assertNotNull(response);

        List<UserDTO> users = response.collect(Collectors.toList());
        assertEquals(existingUsers.size(), users.size());

        for (UserDTO userDto : users) {
            assertNotNull(userDto);
            assertNotNull(userDto.getUsername());

            User existingUser = existingUsers.get(userDto.getUsername());

            assertNotNull(existingUser);
            assertEquals(existingUser.getUsername(), userDto.getUsername());
            assertEquals(existingUser.isSuperAdmin(), userDto.getSuperAdmin());

            // This better be null
            assertNull(userDto.getPassword());
        }
    }

    @Test
    public void testListAllOwners() {
        User user = new User();
        user.setUsername("dummyuser" + TestUtil.randomInt());
        user.setPassword("password");

        this.userCurator.create(user);

        Owner owner1 = this.createOwner();
        Owner owner2 = this.createOwner();

        Role owner1Role = new Role(owner1.getKey() + " role");
        Role owner2Role = new Role(owner2.getKey() + " role");
        owner1Role.addPermission(new PermissionBlueprint(PermissionType.OWNER, owner1, Access.ALL));
        owner1Role.addPermission(new PermissionBlueprint(PermissionType.OWNER, owner2, Access.READ_ONLY));
        owner1Role.addUser(user);
        owner2Role.addUser(user);
        roleCurator.create(owner1Role);
        roleCurator.create(owner2Role);

        // Requesting the list of owners for this user should assume ALL, and not
        // return owner2:
        Stream<OwnerDTO> response = this.userResource.listUserOwners(user.getUsername());

        assertNotNull(response);

        List<OwnerDTO> owners = response.collect(Collectors.toList());

        assertEquals(1, owners.size());
        assertEquals(owner1.getKey(), owners.get(0).getKey());
    }

    @Test
    public void testListOwnersForMySystemsAdmin() {
        User user = new User();
        user.setUsername("dummyuser" + TestUtil.randomInt());
        user.setPassword("password");

        this.userCurator.create(user);

        Owner owner1 = createOwner();

        Role owner1Role = new Role(owner1.getKey() + " role");
        owner1Role.addPermission(new PermissionBlueprint(PermissionType.USERNAME_CONSUMERS,
            owner1, Access.ALL));
        owner1Role.addUser(user);
        roleCurator.create(owner1Role);

        Stream<OwnerDTO> response = this.userResource.listUserOwners(user.getUsername());
        assertNotNull(response);

        List<OwnerDTO> owners = response.collect(Collectors.toList());

        assertEquals(1, owners.size());
        assertEquals(owner1.getKey(), owners.get(0).getKey());
    }

    @Test
    public void testDeleteUser() {
        User user = new User();
        user.setUsername("test-user");
        user.setPassword("banana");
        user.setSuperAdmin(true);

        this.userCurator.create(user);

        this.userResource.deleteUser(user.getUsername());

        User existing = this.userCurator.findByLogin(user.getUsername());
        assertNull(existing);
    }

    @Test
    public void testDeleteUserNotFound() {
        User user = new User();
        user.setUsername("test-user");
        user.setPassword("banana");
        user.setSuperAdmin(true);

        this.userCurator.create(user);

        assertThrows(NotFoundException.class, () -> this.userResource.deleteUser("no such user"));
    }

    @Test
    public void testUpdateUserChangeUsername() {
        User user = new User();
        user.setUsername("test-user");
        user.setPassword("banana");
        user.setSuperAdmin(false);

        this.userCurator.create(user);

        UserDTO update = new UserDTO()
            .username("Luke");

        UserDTO result = this.userResource.updateUser("test-user", update);

        assertEquals("Luke", result.getUsername());
        assertFalse(result.getSuperAdmin());

        // Output should always be null here, so we'll use the direct object to verify
        assertNull(result.getPassword());

        user = this.userCurator.get(user.getId());
        assertNotNull(user);
        assertEquals(Util.hash("banana"), user.getPassword());
    }

    @Test
    public void testUpdateUserChangePassword() {
        User user = new User();
        user.setUsername("test-user");
        user.setPassword("banana");
        user.setSuperAdmin(false);

        this.userCurator.create(user);

        UserDTO update = new UserDTO()
            .password("Skywalker");

        UserDTO result = this.userResource.updateUser("test-user", update);

        assertEquals("test-user", result.getUsername());
        assertFalse(result.getSuperAdmin());

        // Output should always be null here, so we'll use the direct object to verify
        assertNull(result.getPassword());

        user = this.userCurator.get(user.getId());
        assertNotNull(user);
        assertEquals(Util.hash("Skywalker"), user.getPassword());
    }

    @Test
    public void testUpdateUserChangeSuperAdmin() {
        User user = new User();
        user.setUsername("test-user");
        user.setPassword("banana");
        user.setSuperAdmin(false);

        this.userCurator.create(user);

        UserDTO update = new UserDTO()
            .superAdmin(true);

        UserDTO result = this.userResource.updateUser("test-user", update);

        assertEquals("test-user", result.getUsername());
        assertTrue(result.getSuperAdmin());

        // Output should always be null here, so we'll use the direct object to verify
        assertNull(result.getPassword());

        user = this.userCurator.get(user.getId());
        assertNotNull(user);
        assertEquals(Util.hash("banana"), user.getPassword());
    }

    @Test
    public void testUpdateUsersNoLogin() {
        UserDTO dto = new UserDTO()
            .username("henri")
            .password("password");

        assertThrows(NotFoundException.class, () -> this.userResource.updateUser("JarJarIsMyCopilot", dto));
    }

    @Test
    public void testFetchByUsernameUserServiceException() throws UserServiceException {
        UserServiceAdapter adapter = mock(UserServiceAdapter.class);
        UserResource resource = new UserResource(adapter, this.i18n, this.ownerCurator,
            this.modelTranslator);
        doThrow(new UserLoginNotFoundException()).when(adapter).findByLogin(eq("test_user"));
        assertThrows(CandlepinException.class, () -> resource.getUserInfo("test_user"));
    }

    @Test
    public void testCreateUserServiceException() throws UserServiceException {
        UserServiceAdapter adapter = mock(UserServiceAdapter.class);
        UserResource resource = new UserResource(adapter, this.i18n, this.ownerCurator,
            this.modelTranslator);
        doThrow(new UserDisabledException()).when(adapter).findByLogin(eq("test_user"));
        assertThrows(CandlepinException.class, () -> resource.getUserInfo("test_user"));
    }

    @Test
    public void testListUserOwnersUserServiceException() throws UserServiceException {
        UserServiceAdapter adapter = mock(UserServiceAdapter.class);
        UserResource resource = new UserResource(adapter, this.i18n, this.ownerCurator,
            this.modelTranslator);
        doThrow(new UserLoginNotFoundException()).when(adapter).getAccessibleOwners(eq("test_user"));
        assertThrows(CandlepinException.class, () -> resource.getUserInfo("test_user"));
    }
}
