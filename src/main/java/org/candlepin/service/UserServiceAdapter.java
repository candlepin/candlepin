/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.candlepin.service.exception.user.UserDisabledException;
import org.candlepin.service.exception.user.UserLoginNotFoundException;
import org.candlepin.service.exception.user.UserMissingOwnerException;
import org.candlepin.service.exception.user.UserServiceException;
import org.candlepin.service.exception.user.UserUnknownRetrievalException;
import org.candlepin.service.model.OwnerInfo;
import org.candlepin.service.model.PermissionBlueprintInfo;
import org.candlepin.service.model.RoleInfo;
import org.candlepin.service.model.UserInfo;

import java.util.Collection;



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
    boolean validateUser(String username, String password) throws UserServiceException;

    // User life cycle
    UserInfo createUser(UserInfo user);

    UserInfo updateUser(String username, UserInfo user);

    void deleteUser(String username);

    UserInfo findByLogin(String username) throws UserUnknownRetrievalException,
        UserMissingOwnerException, UserDisabledException, UserLoginNotFoundException;

    Collection<? extends UserInfo> listUsers();

    /**
     * Fetches a collection of owners to which the specified user is allowed to register.
     *
     * @param username
     *  The username of the user to check
     *
     * @return
     *  a collection of owners to which the user is allowed to register
     */
    Collection<? extends OwnerInfo> getAccessibleOwners(String username) throws UserDisabledException,
        UserLoginNotFoundException, UserUnknownRetrievalException;

    // Roles
    RoleInfo createRole(RoleInfo role);

    RoleInfo updateRole(String roleName, RoleInfo role);

    RoleInfo addUserToRole(String roleName, String username);

    RoleInfo removeUserFromRole(String roleName, String username);

    RoleInfo addPermissionToRole(String roleName, PermissionBlueprintInfo permission);

    RoleInfo removePermissionFromRole(String roleName, String permissionId);

    void deleteRole(String roleName);

    RoleInfo getRole(String roleName);

    Collection<? extends RoleInfo> listRoles();

}
