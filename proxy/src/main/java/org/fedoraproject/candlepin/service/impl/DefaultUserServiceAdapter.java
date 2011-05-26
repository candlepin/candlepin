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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.LinkedList;

import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerPermission;
import org.fedoraproject.candlepin.model.PermissionCurator;
import org.fedoraproject.candlepin.model.RoleCurator;
import org.fedoraproject.candlepin.model.User;
import org.fedoraproject.candlepin.model.UserCurator;
import org.fedoraproject.candlepin.service.UserServiceAdapter;
import org.fedoraproject.candlepin.util.Util;

import com.google.inject.Inject;

import java.util.ArrayList;
import java.util.Collections;
import org.fedoraproject.candlepin.model.Role;

/**
 * A {@link UserServiceAdapter} implementation backed by a {@link UserCurator}
 * for user creation and persistance.
 */
public class DefaultUserServiceAdapter implements UserServiceAdapter {

    private UserCurator userCurator;
    private RoleCurator roleCurator;
    private PermissionCurator permCurator;
    
    @Inject
    public DefaultUserServiceAdapter(UserCurator userCurator, RoleCurator roleCurator,
        PermissionCurator permCurator) {
        this.userCurator = userCurator;
        this.roleCurator = roleCurator;
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
        Set<OwnerPermission> actualPermissions = new HashSet<OwnerPermission>();

        for (OwnerPermission permission : role.getPermissions()) {
            actualPermissions.add(this.permCurator.findOrCreate(
                    permission.getOwner(), permission.getAccess()));
        }

        role.setPermissions(actualPermissions);
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
        userCurator.delete(user);
    }

    @Override
    public List<User> listByOwner(Owner owner) {
        List<Role> roles = roleCurator.listForOwner(owner);
        Set<User> users = new HashSet<User>();
        for (Role r : roles) {
            users.addAll(r.getUsers());
        }
        return new LinkedList<User>(users);
    }

    @Override
    public User findByLogin(String login) {
        return userCurator.findByLogin(login);
    }

}
