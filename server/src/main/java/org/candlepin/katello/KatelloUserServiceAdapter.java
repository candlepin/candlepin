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

import java.util.List;

import org.candlepin.model.Role;
import org.candlepin.model.User;
import org.candlepin.service.UserServiceAdapter;

/**
 * KatelloUserServiceAdapter
 *
 * Katello handles everything about users before any calls get to candlepin.
 * It will take care of authn/authz, and respond to any calls for roles/permissions etc.
 *
 * Thus, we don't implement anything here.
 */
public class KatelloUserServiceAdapter implements UserServiceAdapter {

    @Override
    public boolean validateUser(String username, String password)
        throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public User createUser(User user) {
        throw new UnsupportedOperationException();
    }

    @Override
    public User updateUser(User user) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteUser(User user) {
        throw new UnsupportedOperationException();
    }

    @Override
    public User findByLogin(String login) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<User> listUsers() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Role createRole(Role r) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Role updateRole(Role r) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void addUserToRole(Role role, User user) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void removeUserFromRole(Role role, User user) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteRole(String roleId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Role getRole(String roleId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public List<Role> listRoles() {
        throw new UnsupportedOperationException();
    }

}
