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
package org.candlepin.katello;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.candlepin.model.Role;
import org.candlepin.model.User;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;


public class KatelloUserServiceAdapterTest {

    private KatelloUserServiceAdapter kusa;

    @BeforeEach
    public void init() {
        kusa = new KatelloUserServiceAdapter();
    }

    @Test
    public void validateUser() {
        assertThrows(UnsupportedOperationException.class, () -> kusa.validateUser("admin", "admin"));
    }

    @Test
    public void createUser() {
        User user = mock(User.class);
        assertThrows(UnsupportedOperationException.class, () -> kusa.createUser(user));
    }

    @Test
    public void updateUser() {
        User user = mock(User.class);
        assertThrows(UnsupportedOperationException.class, () -> kusa.updateUser("username", user));
    }

    @Test
    public void deleteUser() {
        assertThrows(UnsupportedOperationException.class, () -> kusa.deleteUser("username"));
    }

    @Test
    public void findByLogin() throws Exception {
        assertThrows(UnsupportedOperationException.class, () -> kusa.findByLogin("admin"));
    }

    @Test
    public void listUsers() {
        assertThrows(UnsupportedOperationException.class, () -> kusa.listUsers());
    }

    @Test
    public void createRole() throws Exception {
        Role r = mock(Role.class);
        assertThrows(UnsupportedOperationException.class, () -> kusa.createRole(r));
    }

    @Test
    public void updateRole() {
        Role r = mock(Role.class);
        assertThrows(UnsupportedOperationException.class, () -> kusa.updateRole("role name", r));
    }

    @Test
    public void deleteRole() {
        assertThrows(UnsupportedOperationException.class, () -> kusa.deleteRole("roleid"));
    }

    @Test
    public void addUserToRole() {
        assertThrows(UnsupportedOperationException.class, () -> kusa.addUserToRole("role name", "username"));
    }

    @Test
    public void removeUserFromRole() {
        assertThrows(UnsupportedOperationException.class,
            () -> kusa.removeUserFromRole("role name", "username"));
    }

    @Test
    public void getRole() throws Exception {
        assertThrows(UnsupportedOperationException.class, () -> kusa.getRole("roleid"));
    }

    @Test
    public void listRoles() {
        assertThrows(UnsupportedOperationException.class, () -> kusa.listRoles());
    }
}
