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
package org.fedoraproject.candlepin.resteasy.interceptor;

import java.util.List;

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.service.UserServiceAdapter;

import com.google.inject.Injector;
import java.util.ArrayList;
import org.fedoraproject.candlepin.auth.permissions.Permission;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.model.Role;
import org.fedoraproject.candlepin.model.User;
import org.xnap.commons.i18n.I18n;

/**
 * UserAuth
 */
public abstract class UserAuth implements AuthProvider {

    protected UserServiceAdapter userServiceAdapter;
    protected Injector injector;
    protected I18n i18n;

    private static Logger log = Logger.getLogger(UserAuth.class);

    public UserAuth(UserServiceAdapter userServiceAdapter, Injector injector) {
        this.userServiceAdapter = userServiceAdapter;
        this.injector = injector;
        this.i18n = this.injector.getInstance(I18n.class);
    }

    /**
     * Creates a user principal for a given username
     */
    protected Principal createPrincipal(String username) {
        User user = userServiceAdapter.findByLogin(username);
        if (user == null) {
            throw new BadRequestException("user " + username + " not found");
        }

        if (user.isSuperAdmin()) {
            return new UserPrincipal(username, null, true);
        }
        else {
            List<Permission> permissions = new ArrayList<Permission>();

            // flatten out the permissions from the combined roles
            for (Role role : user.getRoles()) {
                permissions.addAll(role.getPermissions());
            }

            Principal principal = new UserPrincipal(username, permissions, false);

            return principal;
        }
    }

}
