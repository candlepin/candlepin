/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.candlepin.service.impl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.candlepin.auth.Access;
import org.candlepin.model.Owner;
import org.candlepin.model.Role;
import org.candlepin.model.RoleCurator;
import org.candlepin.model.User;
import org.candlepin.model.UserCurator;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.impl.DefaultUserServiceAdapter;
import org.candlepin.test.DatabaseTestFixture;
import org.candlepin.test.TestUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

/**
 * DefaultUserServiceAdapterTest
 */
public class DefaultUserServiceAdapterTest extends DatabaseTestFixture {

    private DefaultUserServiceAdapter service;
    private Owner owner;

    @Before
    public void init() {
        super.init();

        this.owner = this.ownerCurator.create(new Owner("default_owner"));

        UserCurator curator = this.injector.getInstance(UserCurator.class);
        this.service = new DefaultUserServiceAdapter(curator, roleCurator,
                permissionCurator);
    }

    @Test
    public void validationPass() {
        User user = new User("test_user", "mypassword");
        this.service.createUser(user);
        Assert.assertTrue(this.service.validateUser("test_user",
                           "mypassword"));
    }

    @Test
    public void testFindAllUsers() {
        User user = new User("test_user", "mypassword");
        this.service.createUser(user);
        List<User> users = this.service.listUsers();
        assertTrue("The size of the list should be 1", users.size() == 1);
        assertEquals("test_user", users.get(0).getUsername());
    }

    @Test
    public void validationBadPassword() {
        User user = new User("dude", "password");
        this.service.createUser(user);

        Assert.assertFalse(this.service.validateUser("dude", "invalid"));
    }

    @Test
    public void validationNoUser() {
        Assert.assertFalse(this.service.validateUser("not_here", "candlepin"));
    }

    @Test
    public void validationNullsAllAround() {
        Assert.assertFalse(this.service.validateUser(null, null));
    }

    @Test
    @Ignore("Find a way to do this with permissions")
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
    @Ignore("Find a way to do this with permissions")
    public void findOwnerFail() {
        //Assert.assertNull(this.service.getOwners("i_dont_exist"));
    }

    @Test
    @Ignore("Find a way to do this with permissions")
    public void ownerAdminRole() {
        User user = new User("regular_user", "password");
        this.service.createUser(user);

        Assert.assertTrue(
            this.service.findByLogin("regular_user").getRoles().contains(Access.ALL));
    }

    @Test
    @Ignore("Find a way to do this with permissions")
    public void superAdminRole() {
        Set<Owner> owners = new HashSet<Owner>();
        owners.add(owner);
        User user = new User("super_admin", "password", true);
        this.service.createUser(user);

        Assert.assertTrue(
            this.service.findByLogin("super_admin").getRoles().contains(Access.ALL));
    }

    @Test
    public void deletionValidationFail() {
        User user = new User("guy", "pass");
        user = this.service.createUser(user);
        this.service.deleteUser(user);

        Assert.assertFalse(this.service.validateUser("guy", "pass"));
    }

    @Test
    public void findByLogin() {
        User u = mock(User.class);
        UserCurator curator = mock(UserCurator.class);
        RoleCurator roleCurator = mock(RoleCurator.class);
        UserServiceAdapter dusa = new DefaultUserServiceAdapter(curator,
                roleCurator, permissionCurator);
        when(curator.findByLogin(anyString())).thenReturn(u);

        User foo = dusa.findByLogin("foo");
        assertNotNull(foo);
        assertEquals(foo, u);
    }

    @Test
    public void addUserToRole() {
        Role adminRole = createAdminRole(owner);
        roleCurator.create(adminRole);
        User user = new User("testuser", "password");
        service.createUser(user);
        service.addUserToRole(adminRole, user);
        adminRole = service.getRole(adminRole.getId());
        assertEquals(1, adminRole.getUsers().size());
    }

    @Test
    public void deleteUserRemovesUserFromRoles() {
        Role adminRole = createAdminRole(owner);
        roleCurator.create(adminRole);
        User user = new User("testuser", "password");
        service.createUser(user);
        service.addUserToRole(adminRole, user);
        service.deleteUser(user);

        adminRole = service.getRole(adminRole.getId());
        assertEquals(0, adminRole.getUsers().size());
    }

    @Test
    public void testUpdating() {
        User user = TestUtil.createUser(null, null, true);
        user = this.userCurator.create(user);
        user.setUsername("JarJar");
        user.setHashedPassword("Binks");
        user.setSuperAdmin(false);
        User updated = service.updateUser(user);
        assertEquals("JarJar", updated.getUsername());
        assertEquals("Binks", updated.getHashedPassword());
        assertFalse(updated.isSuperAdmin());
    }
}
