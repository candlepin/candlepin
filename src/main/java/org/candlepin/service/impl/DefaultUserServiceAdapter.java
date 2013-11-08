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
package org.candlepin.service.impl;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.candlepin.model.PermissionBlueprint;
import org.candlepin.model.Role;
import org.candlepin.model.RoleCurator;
import org.candlepin.model.User;
import org.candlepin.model.UserCurator;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.util.Util;

import com.google.inject.Inject;

/**
 * A {@link UserServiceAdapter} implementation backed by a {@link UserCurator}
 * for user creation and persistance.
 */
public class DefaultUserServiceAdapter implements UserServiceAdapter {

    private UserCurator userCurator;
    private RoleCurator roleCurator;

    @Inject
    public DefaultUserServiceAdapter(UserCurator userCurator, RoleCurator roleCurator) {
        this.userCurator = userCurator;
        this.roleCurator = roleCurator;
    }

    @Override
    public User createUser(User user) {
        return this.userCurator.create(user);
    }

    @Override
    public User updateUser(User user) {
        return userCurator.update(user);
    }

    @Override
    public List<User> listUsers() {
        return this.userCurator.listAll();
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

        for (PermissionBlueprint permission : role.getPermissions()) {
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
