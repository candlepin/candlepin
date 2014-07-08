/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.canadianTenPin.resource.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.canadianTenPin.auth.Access;
import org.canadianTenPin.auth.Principal;
import org.canadianTenPin.auth.UserPrincipal;
import org.canadianTenPin.auth.permissions.OwnerPermission;
import org.canadianTenPin.auth.permissions.Permission;
import org.canadianTenPin.auth.permissions.PermissionFactory.PermissionType;
import org.canadianTenPin.auth.permissions.UsernameConsumersPermission;
import org.canadianTenPin.exceptions.NotFoundException;
import org.canadianTenPin.model.Owner;
import org.canadianTenPin.model.PermissionBlueprint;
import org.canadianTenPin.model.Role;
import org.canadianTenPin.model.User;
import org.canadianTenPin.resource.UserResource;
import org.canadianTenPin.test.DatabaseTestFixture;
import org.canadianTenPin.test.TestUtil;
import org.junit.Before;
import org.junit.Test;

/**
 * UserResourceTest
 */
public class UserResourceTest extends DatabaseTestFixture {

    private UserResource userResource;

    @Before
    public void setUp() {
        userResource = injector.getInstance(UserResource.class);
    }

    @Test
    public void testLookupUser() {

        User user = new User();
        user.setUsername("henri");
        user.setPassword("password");

        userResource.createUser(user);
        User u = userResource.getUserInfo("henri");

        assertEquals(user.getId(), u.getId());
    }

    @Test
    public void testDeleteUser() {

        User user = new User();
        user.setUsername("henri");
        user.setPassword("password");

        userResource.createUser(user);

        userResource.deleteUser("henri");
        assertNull(userResource.getUserInfo("henri"));
    }

    @Test
    public void testListAllOwners() {
        User user = new User();
        user.setUsername("dummyuser" + TestUtil.randomInt());
        user.setPassword("password");
        userResource.createUser(user);

        Owner owner1 = createOwner();
        Owner owner2 = createOwner();

        Role owner1Role = new Role(owner1.getKey() + " role");
        Role owner2Role = new Role(owner2.getKey() + " role");
        owner1Role.addPermission(new PermissionBlueprint(PermissionType.OWNER, owner1,
            Access.ALL));
        owner1Role.addPermission(new PermissionBlueprint(PermissionType.OWNER, owner2,
            Access.READ_ONLY));
        owner1Role.addUser(user);
        owner2Role.addUser(user);
        roleCurator.create(owner1Role);
        roleCurator.create(owner2Role);

        Set<Permission> perms = new HashSet<Permission>();
        perms.add(new OwnerPermission(owner1, Access.ALL));
        perms.add(new OwnerPermission(owner2, Access.READ_ONLY));
        Principal userPrincipal = new UserPrincipal(user.getUsername(), perms, false);

        // Requesting the list of owners for this user should assume ALL, and not
        // return owner2:
        List<Owner> owners = userResource.listUsersOwners(user.getUsername(),
            userPrincipal);
        assertEquals(1, owners.size());
        assertEquals(owner1.getKey(), owners.get(0).getKey());
    }

    @Test
    public void testListOwnersForMySystemsAdmin() {
        User user = new User();
        user.setUsername("dummyuser" + TestUtil.randomInt());
        user.setPassword("password");
        userResource.createUser(user);

        Owner owner1 = createOwner();

        Role owner1Role = new Role(owner1.getKey() + " role");
        owner1Role.addPermission(new PermissionBlueprint(PermissionType.USERNAME_CONSUMERS,
            owner1, Access.ALL));
        owner1Role.addUser(user);
        roleCurator.create(owner1Role);

        Set<Permission> perms = new HashSet<Permission>();
        perms.add(new UsernameConsumersPermission(user, owner1));
        Principal userPrincipal = new UserPrincipal(user.getUsername(), perms, false);

        List<Owner> owners = userResource.listUsersOwners(user.getUsername(),
            userPrincipal);
        assertEquals(1, owners.size());
        assertEquals(owner1.getKey(), owners.get(0).getKey());
    }

    @Test
    public void testListAllUsers() {

        User user = new User();
        user.setUsername("henri");
        user.setPassword("password");

        userResource.createUser(user);
        List<User> users = userResource.list();

        assertTrue("length should be 1", users.size() == 1);
    }

    @Test
    public void testUpdateUsers() {

        User user = new User();
        user.setUsername("henri");
        user.setPassword("password");

        user = userResource.createUser(user);
        user.setUsername("Luke");
        user.setHashedPassword("Skywalker");
        user.setSuperAdmin(true);
        User updated = userResource.updateUser("henri", user);
        assertEquals("Luke", updated.getUsername());
        assertEquals("Skywalker", updated.getHashedPassword());
        assertTrue(updated.isSuperAdmin());
    }

    @Test
    public void testUpdateUsersNoLogin() {
        try {
            User user = new User();
            user.setUsername("henri");
            user.setPassword("password");
            userResource.updateUser("JarJarIsMyCopilot", user);
        }
        catch (NotFoundException e) {
            // this is exptected
            return;
        }
        fail("No exception was thrown");
    }
}
