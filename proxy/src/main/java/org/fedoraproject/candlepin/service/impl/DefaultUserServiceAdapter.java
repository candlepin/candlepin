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

import java.util.LinkedList;
import java.util.List;

import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.User;
import org.fedoraproject.candlepin.model.UserCurator;
import org.fedoraproject.candlepin.service.UserServiceAdapter;

import com.google.inject.Inject;

/**
 * A {@link UserServiceAdapter} implementation backed by a {@link UserCurator}
 * for user creation and persistance.
 */
public class DefaultUserServiceAdapter implements UserServiceAdapter {

    private UserCurator userCurator;
    
    @Inject
    public DefaultUserServiceAdapter(UserCurator userCurator) {
        this.userCurator = userCurator;
    }
    
    @Override
    public User createUser(User user) {
        return this.userCurator.create(user);
    }
    
    @Override
    public Owner getOwner(String username) {
        User user = this.userCurator.findByLogin(username);
        
        return user.getOwner();
    }

    @Override
    public List<Role> getRoles(String username) {
        List<Role> roles = new LinkedList<Role>();
        User user = this.userCurator.findByLogin(username);
        
        // this might not be the best way to do this
        // should we have a list of roles stored rather than this flag?
        if (user.isSuperAdmin()) {
            roles.add(Role.SUPER_ADMIN);
        }
        else {
            roles.add(Role.OWNER_ADMIN);
        }
        
        return roles;
    }

    @Override
    public boolean validateUser(String username, String password) {
        User user = this.userCurator.findByLogin(username);
    
        if (user != null && password != null) {
            return password.equals(user.getPassword());
        }
        
        return false;
    }


}
