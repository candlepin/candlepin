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
package org.candlepin.ldap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.novell.ldap.LDAPConnection;
import com.novell.ldap.LDAPException;

import org.candlepin.config.Config;
import org.candlepin.model.Role;
import org.candlepin.model.User;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import java.util.List;


/**
 * LDAPUserServiceAdapterTest
 */
public class LDAPUserServiceAdapterTest {

    private LDAPUserServiceAdapter lusa;
    private Config config;

    @Before
    public void init() {
        config = mock(Config.class);
        when(config.getString(eq("ldap.base"), any(String.class)))
            .thenReturn("dc=test,dc=com");
        when(config.getInt(eq("ldap.port"))).thenReturn(9898);
        when(config.getString(eq("ldap.host"), any(String.class)))
            .thenReturn("localhost");
        lusa = new LDAPUserServiceAdapter(config);
    }

    @Test
    public void getDN() {
        assertEquals("uid=foomanchu,dc=test,dc=com", lusa.getDN("foomanchu"));
    }

    @Ignore("needs mock LDAP server") @Test
    public void validateUser() {
        assertTrue(lusa.validateUser("user", "securepassword"));
    }

    @Ignore("needs mock LDAP server") @Test
    public void getConnection() throws LDAPException {
        LDAPConnection conn = lusa.getConnection();
        assertNotNull(conn);
    }

    @Ignore("needs mock LDAP server") @Test
    public void findByLogin() {
        User user = lusa.findByLogin("foo");
        assertNotNull(user);
    }

    @Test
    public void deleteUser() {
        User user = mock(User.class);
        lusa.deleteUser(user);
    }

    @Test
    public void createUser() {
        User user = mock(User.class);
        assertEquals(user, lusa.createUser(user));
    }

    @Test
    public void updateUser() {
        User user = mock(User.class);
        assertEquals(user, lusa.updateUser(user));
    }

    @Test
    public void listUsers() {
        List<User> list = lusa.listUsers();
        assertNotNull(list);
        assertTrue(list.isEmpty());
    }

    @Test(expected = UnsupportedOperationException.class)
    public void deleteRole() {
        lusa.deleteRole("foo");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void createRole() {
        Role r = mock(Role.class);
        lusa.createRole(r);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void listRoles() {
        lusa.listRoles();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void updateRole() {
        Role r = mock(Role.class);
        lusa.updateRole(r);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getRole() {
        lusa.getRole("foo");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void addUserToRole() {
        Role r = mock(Role.class);
        User user = mock(User.class);
        lusa.addUserToRole(r, user);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void removeUserFromRole() {
        Role r = mock(Role.class);
        User user = mock(User.class);
        lusa.removeUserFromRole(r, user);
    }
}
