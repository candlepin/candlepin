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
package org.candlepin.katello;

import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.model.OwnerInfo;
import org.candlepin.service.model.PermissionBlueprintInfo;
import org.candlepin.service.model.RoleInfo;
import org.candlepin.service.model.UserInfo;

import java.util.Collection;



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
    public boolean validateUser(String username, String password) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UserInfo createUser(UserInfo user) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UserInfo updateUser(String username, UserInfo user) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteUser(String username) {
        throw new UnsupportedOperationException();
    }

    @Override
    public UserInfo findByLogin(String username) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<? extends UserInfo> listUsers() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<? extends OwnerInfo> getAccessibleOwners(String username) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RoleInfo createRole(RoleInfo role) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RoleInfo updateRole(String roleName, RoleInfo role) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RoleInfo addUserToRole(String roleName, String username) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RoleInfo removeUserFromRole(String roleName, String username) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RoleInfo addPermissionToRole(String roleName, PermissionBlueprintInfo permission) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RoleInfo removePermissionFromRole(String roleName, String permissionId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void deleteRole(String roleName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public RoleInfo getRole(String roleName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Collection<? extends RoleInfo> listRoles() {
        throw new UnsupportedOperationException();
    }

}
