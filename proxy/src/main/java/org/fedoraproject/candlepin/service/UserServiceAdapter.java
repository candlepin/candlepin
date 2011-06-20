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
package org.fedoraproject.candlepin.service;

import java.util.List;

import org.fedoraproject.candlepin.model.Role;
import org.fedoraproject.candlepin.model.User;

/**
 * UserServiceAdapter
 */
public interface UserServiceAdapter {

    /**
     * Does this user service implementation support creation/deletion
     * of {@link User}s?
     *
     * @return if this implementation prohibits user creation/deletion via
     *          this API
     */
    boolean isReadyOnly();

    /**
     * Validates the credentials of a given user
     * @param username
     * @param password
     * @return true if username and password combination are valid.
     * @throws Exception if there was an error validating the user
     */
    boolean validateUser(String username, String password) throws Exception;

    User createUser(User user);

    void deleteUser(User user);

    User findByLogin(String login);

    Role createRole(Role r);

    Role updateRole(Role r);

    void addUserToRole(Role role, User user);

    void removeUserFromRole(Role role, User user);

    void deleteRole(String roleId);

    List<Role> getRoles(String username);

    Role getRole(String roleId);

    List<Role> listRoles();

}
