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
package org.fedoraproject.candlepin.service.impl.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.User;
import org.fedoraproject.candlepin.model.UserCurator;
import org.fedoraproject.candlepin.service.UserServiceAdapter;
import org.fedoraproject.candlepin.service.impl.DefaultUserServiceAdapter;
import org.fedoraproject.candlepin.test.DatabaseTestFixture;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

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
        this.service = new DefaultUserServiceAdapter(curator);
    }
    
    @Test
    public void validationPass() {
        User user = new User("test_user", "mypassword");
        this.service.createUser(user);
        Assert.assertTrue(this.service.validateUser("test_user",
                           "mypassword"));
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
    public void findOwner() {
        User user = new User("test_name", "password");
        user.addMembershipTo(owner);
        this.service.createUser(user);
        
        List<Owner> owners = this.service.getOwners("test_name");
        Assert.assertEquals(1, owners.size());
        Assert.assertEquals(owner, owners.get(0));
    }
    
    @Test
    public void findOwnerFail() {
        Assert.assertNull(this.service.getOwners("i_dont_exist"));
    }
    
    @Test
    public void ownerAdminRole() {
        User user = new User("regular_user", "password");
        this.service.createUser(user);
        
        Assert.assertTrue(this.service.getRoles("regular_user").contains(Role.OWNER_ADMIN));
    }
    
    @Test
    public void superAdminRole() {
        Set<Owner> owners = new HashSet<Owner>();
        owners.add(owner);
        User user = new User("super_admin", "password", true);
        this.service.createUser(user);
        
        Assert.assertTrue(this.service.getRoles("super_admin").contains(Role.SUPER_ADMIN));
    }
    
    @Test
    public void emtpyRolesForNoLogin() {
        Assert.assertArrayEquals(new Role[] {}, this.service.getRoles("made_up").toArray());
    }
    
    @Test
    public void deletionValidationFail() {
        User user = new User("guy", "pass");
        user = this.service.createUser(user);
        this.service.deleteUser(user);
        
        Assert.assertFalse(this.service.validateUser("guy", "pass"));
    }
    
    @Test
    public void listByDeletedOwner() {
        this.ownerCurator.delete(owner);
        Assert.assertArrayEquals(new User[] {}, this.service.listByOwner(owner).toArray());
    }
    
    @Test
    public void listByOwnerSingle() {
        User user = new User("dude", "man");
        user.addMembershipTo(owner);
        this.service.createUser(user);
        
        Assert.assertArrayEquals(new User[] {user}, 
            this.service.listByOwner(owner).toArray());
    }
    
    @Test
    public void listByOwnerMultiple() {
        List<User> users = new ArrayList<User>();
     
        for (int i = 0; i < 5; i++) {
            User user = new User("user" + i, "password" + i);
            user.addMembershipTo(owner);
            this.service.createUser(user);
            
            users.add(user);
        }
        
        // add in a few others to filter out
        Owner different = new Owner("different_owner");
        this.ownerCurator.create(different);

        User user = new User("different_user", "password");
        user.addMembershipTo(different);
        this.service.createUser(user);

        user = new User("another_different_user", "pass");
        user.addMembershipTo(different);
        this.service.createUser(user);
        
        Assert.assertArrayEquals(users.toArray(), 
            this.service.listByOwner(owner).toArray());
    }
    
    @Test
    public void findByLogin() {
        User u = mock(User.class);
        UserCurator curator = mock(UserCurator.class);
        UserServiceAdapter dusa = new DefaultUserServiceAdapter(curator);
        when(curator.findByLogin(anyString())).thenReturn(u);
        
        User foo = dusa.findByLogin("foo");
        assertNotNull(foo);
        assertEquals(foo, u);
    }
}
