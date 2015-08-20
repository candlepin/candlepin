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
package org.candlepin.resteasy.interceptor;

import org.candlepin.auth.Principal;
import org.candlepin.auth.UserPrincipal;
import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.model.User;
import org.candlepin.service.UserServiceAdapter;

import org.xnap.commons.i18n.I18n;

import javax.inject.Inject;
import javax.inject.Provider;

/**
 * UserAuth
 */
public abstract class UserAuth implements AuthProvider {

    protected UserServiceAdapter userServiceAdapter;
    protected Provider<I18n> i18n;

    @Inject
    public UserAuth(UserServiceAdapter userServiceAdapter, Provider<I18n> i18n) {
        this.userServiceAdapter = userServiceAdapter;
        this.i18n = i18n;
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
            Principal principal = new UserPrincipal(username, user.getPermissions(), false);

            return principal;
        }
    }

}
