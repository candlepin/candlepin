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
package org.candlepin.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.candlepin.auth.Access;
import org.candlepin.model.Owner;
import org.candlepin.model.Role;
import org.candlepin.model.RoleCurator;
import org.candlepin.model.User;
import org.candlepin.model.UserCurator;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.exception.user.UserServiceException;
import org.candlepin.service.model.RoleInfo;
import org.candlepin.service.model.UserInfo;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;



/**
 * DefaultUserServiceAdapterTest
 */
public class DefaultUserServiceAdapterTest extends DatabaseTestFixture {
    private DefaultUserServiceAdapter service;
    private Owner owner;

    @BeforeEach
    @Override
    public void init() throws Exception {
        super.init();
        this.owner = this.createOwner("default_owner");
        this.service = new DefaultUserServiceAdapter(userCurator, roleCurator, permissionBlueprintCurator,
            ownerCurator, permissionFactory);
    }

    @Test
    public void validationPass() {
        User user = new User("test_user", "mypassword");
        this.service.createUser(user);
        assertTrue(this.service.validateUser("test_user", "mypassword"));
    }

    @Test
    public void testFindAllUsers() {
        List<? extends UserInfo> users = this.service.listUsers();

        // The default super-admin is always created by default, and should be present
        assertEquals(1, users.size());
        assertEquals("admin", users.get(0).getUsername());

        User user = new User("test_user", "mypassword");
        this.service.createUser(user);

        users = this.service.listUsers();
        assertEquals(2, users.size());

        Set<String> usernames = users.stream()
            .map(UserInfo::getUsername)
            .collect(Collectors.toSet());

        assertEquals(Set.of("admin", "test_user"), usernames);
    }

    @Test
    public void validationBadPassword() {
        User user = new User("dude", "password");
        this.service.createUser(user);

        assertFalse(this.service.validateUser("dude", "invalid"));
    }

    @Test
    public void validationNoUser() {
        assertFalse(this.service.validateUser("not_here", "candlepin"));
    }

    @Test
    public void validationNullsAllAround() {
        assertFalse(this.service.validateUser(null, null));
    }

    @Test
    @Disabled("Find a way to do this with permissions")
    public void findOwner() {
        User user = new User("test_name", "password");

        this.service.createUser(user);

        Role adminRole = createAdminRole(owner);
        adminRole.addUser(user);
        roleCurator.create(adminRole);
    }

    @Test
    @Disabled("Find a way to do this with permissions")
    public void findOwnerFail() {
        // Assert.assertNull(this.service.getOwners("i_dont_exist"));
    }

    @Test
    @Disabled("Find a way to do this with permissions")
    public void ownerAdminRole() {
        User user = new User("regular_user", "password");
        this.service.createUser(user);

        assertTrue(
            this.service.findByLogin("regular_user").getRoles().contains(Access.ALL.name()));
    }

    @Test
    @Disabled("Find a way to do this with permissions")
    public void superAdminRole() {
        Set<Owner> owners = new HashSet<>();
        owners.add(owner);
        User user = new User("super_admin", "password", true);
        this.service.createUser(user);

        assertTrue(
            this.service.findByLogin("super_admin").getRoles().contains(Access.ALL.name()));
    }

    @Test
    public void deletionValidationFail() {
        User user = new User("guy", "pass");
        UserInfo created = this.service.createUser(user);
        this.service.deleteUser(user.getUsername());

        assertFalse(this.service.validateUser("guy", "pass"));
    }

    @Test
    public void findByLogin() throws UserServiceException {
        User u = mock(User.class);
        UserCurator curator = mock(UserCurator.class);
        RoleCurator roleCurator = mock(RoleCurator.class);
        UserServiceAdapter dusa = new DefaultUserServiceAdapter(curator, roleCurator,
            permissionBlueprintCurator, ownerCurator, permissionFactory);
        when(curator.findByLogin(anyString())).thenReturn(u);

        UserInfo foo = dusa.findByLogin("foo");
        assertNotNull(foo);
        assertEquals(foo, u);
    }

    @Test
    public void addUserToRole() {
        Role adminRole = createAdminRole(owner);
        roleCurator.create(adminRole);
        User user = new User("testuser", "password");
        service.createUser(user);
        service.addUserToRole(adminRole.getName(), user.getUsername());
        RoleInfo updated = service.getRole(adminRole.getName());
        assertEquals(1, updated.getUsers().size());
    }

    @Test
    public void deleteUserRemovesUserFromRoles() {
        Role adminRole = createAdminRole(owner);
        roleCurator.create(adminRole);
        User user = new User("testuser", "password");
        service.createUser(user);
        service.addUserToRole(adminRole.getName(), user.getUsername());
        service.deleteUser(user.getUsername());

        RoleInfo updated = service.getRole(adminRole.getName());
        assertEquals(0, updated.getUsers().size());
    }

    @Test
    public void testUpdating() {
        User user = TestUtil.createUser(null, null, true);
        user = this.userCurator.create(user);
        user.setUsername("JarJar");
        user.setHashedPassword("Binks");
        user.setSuperAdmin(false);
        UserInfo updated = service.updateUser(user.getUsername(), user);
        assertEquals("JarJar", updated.getUsername());
        assertEquals("Binks", updated.getHashedPassword());
        assertFalse(updated.isSuperAdmin());
    }
}
