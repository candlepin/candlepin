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
package org.fedoraproject.candlepin.resource.test;

import org.fedoraproject.candlepin.model.User;
import org.fedoraproject.candlepin.resource.UserResource;

import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;


/**
 * UserResourceTest
 * @version $Rev$
 */
public class UserResourceTest {
    
    private UserResource api = new UserResource();
   
    @Test
    public void testNewUser() {
        User user = api.create("candlepin", "cp_p@$sw0rd");
        assertNotNull(user);
        assertEquals("candlepin", user.getLogin());
        assertEquals("cp_p@$sw0rd", user.getPassword());
        
        user = api.create(null, null);
        assertNotNull(user);
        assertEquals(null, user.getLogin());
        assertEquals(null, user.getPassword());
        
        user = api.create("", "");
        assertNotNull(user);
        assertEquals("", user.getLogin());
        assertEquals("", user.getPassword());
    }
    
    @Test
    public void testList() {
        List<User> users = api.list();
        int origSize = users.size();
        // create 1
        api.create("candlepin", "cp_p@$sw0rd");
        
        // create 2
        api.create("jesusr", "n0P@$sw0rD");
        
        // get the list back
        users = api.list();
        System.out.println("Users: " + users.toString());
        assertNotNull(users);
        assertEquals(origSize + 2, users.size());
        assertEquals(User.class, users.get(0).getClass());
    }
    
    @Test
    public void testGet() {
        User user = api.get("test-login");
        assertNotNull(user);
        assertEquals("test-login", user.getLogin());
    }

}
