/**
 * Copyright (c) 2008 Red Hat, Inc.
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
package org.fedoraproject.candlepin.api.test;

import com.sun.jersey.api.representation.Form;

import org.fedoraproject.candlepin.api.UserApi;
import org.fedoraproject.candlepin.model.User;

import junit.framework.TestCase;


/**
 * UserApiTest
 * @version $Rev$
 */
public class UserApiTest extends TestCase {
    private UserApi api = new UserApi();
    
    public void testNewUser() {
        Form f = new Form();
        f.add("login", "candlepin");
        f.add("password", "cp_p@$sw0rd");
        User user = (User) api.create(f);
        assertNotNull(user);
        assertEquals("candlepin", user.getLogin());
        assertEquals("cp_p@$sw0rd", user.getPassword());
        
        f.clear();
        f.add("login", null);
        f.add("password", null);
        user = (User) api.create(f);
        assertNotNull(user);
        assertEquals("", user.getLogin());
        assertEquals("", user.getPassword());
        
        f.clear();
        f.add("login", "");
        f.add("password", "");
        user = (User) api.create(f);
        assertNotNull(user);
        assertEquals("", user.getLogin());
        assertEquals("", user.getPassword());
    }
    
//    public void testList() {
//        List<Object> users = api.list();
//        int origSize = users.length;
//        // create 1
//        Form f = new Form();
//        f.add("login", "candlepin");
//        f.add("password", "cp_p@$sw0rd");
//        api.create(f);
//        
//        // create 2
//        f.clear();
//        f.add("login", "jesusr");
//        f.add("password", "n0P@$sw0rD");
//        api.create(f);
//        
//        // get the list back
//        users = api.list();
//        System.out.println("Users: " + users.toString());
//        assertNotNull(users);
//        assertEquals(origSize + 2, users.length);
//        assertEquals(User.class, users[0].getClass());
//    }
    
    public void testGet() {
        User user = api.get("test-login");
        assertNotNull(user);
        assertEquals("test-login", user.getLogin());
    }
}
