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
import org.candlepin.model.PermissionBlueprintCurator;
import org.candlepin.model.Role;
import org.candlepin.model.RoleCurator;
import org.candlepin.model.User;
import org.candlepin.model.UserCurator;
import org.candlepin.service.UserServiceAdapter;
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

import javax.inject.Inject;

/**
 * DefaultUserServiceAdapterTest
 */
public class DefaultUserServiceAdapterTest extends DatabaseTestFixture {
    @Inject protected PermissionBlueprintCurator permissionCurator;

    private DefaultUserServiceAdapter service;
    private Owner owner;

    @BeforeEach
    @Override
    public void init() throws Exception {
        super.init();
        this.owner = ownerCurator.create(new Owner("default_owner"));
        this.service = new DefaultUserServiceAdapter(userCurator, roleCurator, permissionCurator,
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
        User user = new User("test_user", "mypassword");
        this.service.createUser(user);
        List<? extends UserInfo> users = this.service.listUsers();
        assertEquals(1, users.size());
        assertEquals("test_user", users.get(0).getUsername());
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

        //List<Owner> owners = this.service.getOwners("test_name");
        //Assert.assertEquals(1, owners.size());
        //Assert.assertEquals(owner, owners.get(0));
    }

    @Test
    @Disabled("Find a way to do this with permissions")
    public void findOwnerFail() {
        //Assert.assertNull(this.service.getOwners("i_dont_exist"));
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
    public void findByLogin() {
        User u = mock(User.class);
        UserCurator curator = mock(UserCurator.class);
        RoleCurator roleCurator = mock(RoleCurator.class);
        UserServiceAdapter dusa = new DefaultUserServiceAdapter(curator, roleCurator, permissionCurator,
            ownerCurator, permissionFactory);
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


    // @Test
    // public void testGetAccessibleOwners() {
    //     String username = "TESTUSER";
    //     String password = "sekretpassword";
    //     Owner owner1 = new Owner("owner1", "owner one");
    //     Owner owner2 = new Owner("owner2", "owner two");
    //     User user = new User(username, password);

    //     Set<Owner> owners = user.getOwners(null, Access.ALL);
    //     assertEquals(0, owners.size());
    //     user.addPermissions(new TestPermission(owner1));
    //     user.addPermissions(new TestPermission(owner2));

    //     // Adding the new permissions should give us access
    //     // to both new owners
    //     owners = user.getOwners(null, Access.ALL);
    //     assertEquals(2, owners.size());
    // }

    // @Test
    // public void testGetAccessibleOwnersCoversCreateConsumers() {
    //     String username = "TESTUSER";
    //     String password = "sekretpassword";
    //     Owner owner1 = new Owner("owner1", "owner one");
    //     Owner owner2 = new Owner("owner2", "owner two");
    //     User user = new User(username, password);

    //     Set<Owner> owners = user.getOwners(null, Access.ALL);
    //     assertEquals(0, owners.size());
    //     user.addPermissions(new TestPermission(owner1));
    //     user.addPermissions(new TestPermission(owner2));

    //     // This is the check we do in API call, make sure owner admins show up as
    //     // having perms to create consumers as well:
    //     owners = user.getOwners(SubResource.CONSUMERS, Access.CREATE);
    //     assertEquals(2, owners.size());
    // }

    // @Test
    // public void testGetAccessibleOwnersNonOwnerPerm() {
    //     String username = "TESTUSER";
    //     String password = "sekretpassword";
    //     Owner owner1 = new Owner("owner1", "owner one");
    //     Owner owner2 = new Owner("owner2", "owner two");
    //     User user = new User(username, password);

    //     Set<Owner> owners = user.getOwners(null, Access.ALL);
    //     assertEquals(0, owners.size());
    //     user.addPermissions(new OtherPermission(owner1));
    //     user.addPermissions(new OtherPermission(owner2));

    //     // Adding the new permissions should not give us access
    //     // to either of the new owners
    //     owners = user.getOwners(null, Access.ALL);
    //     assertEquals(0, owners.size());
    // }

    // private class TestPermission implements Permission {

    //     private Owner owner;

    //     public TestPermission(Owner o) {
    //         owner = o;
    //     }

    //     @Override
    //     public boolean canAccess(Object target, SubResource subResource,
    //         Access access) {
    //         if (target instanceof Owner) {
    //             Owner targetOwner = (Owner) target;
    //             return targetOwner.getKey().equals(this.getOwner().getKey());
    //         }
    //         return false;
    //     }

    //     @Override
    //     public Criterion getCriteriaRestrictions(Class entityClass) {
    //         return null;
    //     }

    //     @Override
    //     public Owner getOwner() {
    //         return owner;
    //     }
    // }

    // private class OtherPermission implements Permission {

    //     private Owner owner;

    //     public OtherPermission(Owner o) {
    //         owner = o;
    //     }

    //     @Override
    //     public boolean canAccess(Object target, SubResource subResource,
    //         Access access) {
    //         return false;
    //     }

    //     @Override
    //     public Criterion getCriteriaRestrictions(Class entityClass) {
    //         return null;
    //     }

    //     @Override
    //     public Owner getOwner() {
    //         return owner;
    //     }
    // }
}
