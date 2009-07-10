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
        User user = api.create("candlepin", "cp_p@$sw0rd");
        assertNotNull(user);
        assertEquals("candlepin", user.getLogin());
        assertEquals("cp_p@$sw0rd", user.getPassword());
        
        user = api.create(null, null);
        assertNotNull(user);
        assertNull(user.getLogin());
        assertNull(user.getPassword());
        
        user = api.create("", "");
        assertNotNull(user);
        assertEquals("", user.getLogin());
        assertEquals("", user.getPassword());
    }
}
