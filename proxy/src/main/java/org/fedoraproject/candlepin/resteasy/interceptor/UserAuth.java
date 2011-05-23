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

import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.service.UserServiceAdapter;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Injector;
import java.util.ArrayList;
import org.fedoraproject.candlepin.model.Role;
import org.fedoraproject.candlepin.model.OwnerPermission;

/**
 * UserAuth
 */
public abstract class UserAuth implements AuthProvider {

    protected UserServiceAdapter userServiceAdapter;
    protected OwnerCurator ownerCurator;
    protected Injector injector;
    protected I18n i18n;

    public UserAuth(UserServiceAdapter userServiceAdapter,
        OwnerCurator ownerCurator, Injector injector) {
        this.userServiceAdapter = userServiceAdapter;
        this.ownerCurator = ownerCurator;
        this.injector = injector;
        i18n = this.injector.getInstance(I18n.class);
    }

    /**
     * Creates a user principal for a given username
     */
    protected Principal createPrincipal(String username) {
        List<OwnerPermission> permissions = new ArrayList<OwnerPermission>();

        // flatten out the permissions from the combined roles
        for (Role role : this.userServiceAdapter.getRoles(username)) {
            permissions.addAll(role.getPermissions());
        }

        Principal principal = new UserPrincipal(username, permissions);

        // TODO:  Look up owner here?
        // Old code was doing this:  fullOwners.add(AuthUtil.lookupOwner(owner, ownerCurator));

        return principal;
    }

}
