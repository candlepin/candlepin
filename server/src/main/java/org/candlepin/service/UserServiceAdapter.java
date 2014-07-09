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
package org.candlepin.service;

import java.util.List;

import org.candlepin.model.Role;
import org.candlepin.model.User;

/**
 * UserServiceAdapter
 */
public interface UserServiceAdapter {

    /**
     * Validates the credentials of a given user
     * @param username
     * @param password
     * @return true if username and password combination are valid.
     * @throws Exception if there was an error validating the user
     */
    boolean validateUser(String username, String password) throws Exception;

    // User life cycle
    User createUser(User user);

    User updateUser(User user);

    void deleteUser(User user);

    User findByLogin(String login);

    List<User> listUsers();

    Role createRole(Role r);

    Role updateRole(Role r);

    void addUserToRole(Role role, User user);

    void removeUserFromRole(Role role, User user);

    void deleteRole(String roleId);

    Role getRole(String roleId);

    List<Role> listRoles();

}
