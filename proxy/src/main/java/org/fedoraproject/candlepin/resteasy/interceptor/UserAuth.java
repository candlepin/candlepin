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
import org.fedoraproject.candlepin.auth.Role;
import org.fedoraproject.candlepin.auth.UserPrincipal;
import org.fedoraproject.candlepin.exceptions.BadRequestException;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.OwnerCurator;
import org.fedoraproject.candlepin.service.UserServiceAdapter;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Injector;
import java.util.ArrayList;

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
        List<Owner> owners = this.userServiceAdapter.getOwners(username);
        UserPrincipal principal = null;
        if (owners == null || owners.isEmpty()) {
            String msg = i18n.tr("No owners found for user {0}", username);
            throw new BadRequestException(msg);
        }
        else {
            List<Owner> fullOwners = new ArrayList<Owner>();
            for (Owner owner : owners) {
                fullOwners.add(AuthUtil.lookupOwner(owner, ownerCurator));
            }
            List<Role> roles = this.userServiceAdapter.getRoles(username);
            principal = new UserPrincipal(username, fullOwners, roles);
        }
        return principal;
    }

}
