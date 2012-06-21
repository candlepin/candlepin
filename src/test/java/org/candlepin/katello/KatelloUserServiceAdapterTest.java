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
package org.candlepin.katello;

import static org.mockito.Mockito.mock;

import org.candlepin.model.Role;
import org.candlepin.model.User;
import org.junit.Before;
import org.junit.Test;

/**
 * KatelloUserServiceAdapterTest
 */
public class KatelloUserServiceAdapterTest {

    private KatelloUserServiceAdapter kusa;

    @Before
    public void init() {
        kusa = new KatelloUserServiceAdapter();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void validateUser() throws Exception {
        kusa.validateUser("admin", "admin");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void createUser() throws Exception {
        User user = mock(User.class);
        kusa.createUser(user);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void updateUser() throws Exception {
        User user = mock(User.class);
        kusa.updateUser(user);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void deleteUser() throws Exception {
        User user = mock(User.class);
        kusa.deleteUser(user);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void findByLogin() throws Exception {
        kusa.findByLogin("admin");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void listUsers() throws Exception {
        kusa.listUsers();
    }

    @Test(expected = UnsupportedOperationException.class)
    public void createRole() throws Exception {
        Role r = mock(Role.class);
        kusa.createRole(r);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void updateRole() throws Exception {
        Role r = mock(Role.class);
        kusa.updateRole(r);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void deleteRole() throws Exception {
        kusa.deleteRole("roleid");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void addUserToRole() throws Exception {
        Role r = mock(Role.class);
        User user = mock(User.class);
        kusa.addUserToRole(r, user);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void removeUserFromRole() throws Exception {
        Role r = mock(Role.class);
        User user = mock(User.class);
        kusa.removeUserFromRole(r, user);
    }

    @Test(expected = UnsupportedOperationException.class)
    public void getRole() throws Exception {
        kusa.getRole("roleid");
    }

    @Test(expected = UnsupportedOperationException.class)
    public void listRoles() throws Exception {
        kusa.listRoles();
    }
}
