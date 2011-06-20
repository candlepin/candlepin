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
package org.fedoraproject.candlepin.service.impl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.fedoraproject.candlepin.model.OwnerPermission;
import org.fedoraproject.candlepin.model.OwnerPermissionCurator;
import org.fedoraproject.candlepin.model.Role;
import org.fedoraproject.candlepin.model.RoleCurator;
import org.fedoraproject.candlepin.model.User;
import org.fedoraproject.candlepin.model.UserCurator;
import org.fedoraproject.candlepin.service.UserServiceAdapter;
import org.fedoraproject.candlepin.util.Util;

import com.google.inject.Inject;

/**
 * A {@link UserServiceAdapter} implementation backed by a {@link UserCurator}
 * for user creation and persistance.
 */
public class DefaultUserServiceAdapter implements UserServiceAdapter {

    private UserCurator userCurator;
    private RoleCurator roleCurator;
    private OwnerPermissionCurator permCurator;

    @Inject
    public DefaultUserServiceAdapter(UserCurator userCurator, RoleCurator roleCurator,
        OwnerPermissionCurator permCurator) {
        this.userCurator = userCurator;
        this.roleCurator = roleCurator;
        this.permCurator = permCurator;
    }

    @Override
    public User createUser(User user) {
        return this.userCurator.create(user);
    }

    @Override
    public List<Role> getRoles(String username) {
        User user = this.userCurator.findByLogin(username);

        if (user != null) {
            return new ArrayList<Role>(user.getRoles());
        }

        return Collections.emptyList();
    }

    @Override
    public List<Role> listRoles() {
        return roleCurator.listAll();
    }

    @Override
    public Role createRole(Role role) {
        Set<User> actualUsers = new HashSet<User>();

        for (User user : role.getUsers()) {
            User actualUser = findByLogin(user.getUsername());
            actualUsers.add(actualUser);
        }
        role.setUsers(actualUsers);

        for (OwnerPermission permission : role.getPermissions()) {
            permission.setRole(role);
        }

        this.roleCurator.create(role);
        return role;
    }

    @Override
    public boolean validateUser(String username, String password) {
        User user = this.userCurator.findByLogin(username);
        String hashedPassword = Util.hash(password);

        if (user != null && password != null && hashedPassword != null) {
            return hashedPassword.equals(user.getHashedPassword());
        }

        return false;
    }

    @Override
    public boolean isReadyOnly() {
        return false;
    }

    @Override
    public void deleteUser(User user) {
        for (Role r : user.getRoles()) {
            user.removeRole(r);
        }
        userCurator.delete(user);
    }

    @Override
    public User findByLogin(String login) {
        return userCurator.findByLogin(login);
    }

    @Override
    public void deleteRole(String roleId) {
        Role r = roleCurator.find(roleId);
        roleCurator.delete(r);
    }

    @Override
    public Role updateRole(Role r) {
//        Set<OwnerPermission> newPermissions = new HashSet<OwnerPermission>();
//        for (OwnerPermission incomingPerm : r.getPermissions()) {
//            newPermissions.add(this.permCurator.findOrCreate(
//                incomingPerm.getOwner(), incomingPerm.getAccess()));
//        }
//        r.getPermissions().clear();
//        r.getPermissions().addAll(newPermissions);
        return roleCurator.merge(r);
    }

    @Override
    public Role getRole(String roleId) {
        return roleCurator.find(roleId);
    }

    @Override
    public void addUserToRole(Role role, User user) {
        role.addUser(user);
        roleCurator.merge(role);
    }

    @Override
    public void removeUserFromRole(Role role, User user) {
        role.removeUser(user);
        roleCurator.merge(role);
    }
}
