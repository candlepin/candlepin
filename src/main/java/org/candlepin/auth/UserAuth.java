/*
 * Copyright (c) 2009 - 2026 Red Hat, Inc.
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
package org.candlepin.auth;

import org.candlepin.auth.permissions.PermissionFactory;
import org.candlepin.exceptions.BadRequestException;
import org.candlepin.resource.util.UserServiceExceptionTranslator;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.exception.user.UserServiceException;
import org.candlepin.service.model.UserInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.core.Response.Status;

/**
 * UserAuth
 */
public abstract class UserAuth implements AuthProvider {
    private static final Logger log = LoggerFactory.getLogger(UserAuth.class);

    protected UserServiceAdapter userServiceAdapter;
    protected Provider<I18n> i18nProvider;
    protected PermissionFactory permissionFactory;

    @Inject
    public UserAuth(UserServiceAdapter userServiceAdapter, Provider<I18n> i18nProvider,
        PermissionFactory permissionFactory) {

        this.userServiceAdapter = userServiceAdapter;
        this.i18nProvider = i18nProvider;
        this.permissionFactory = permissionFactory;
    }

    /**
     * Creates a user principal for a given username, returning unauthorized for unexpected user service failures
     *
     * @param username
     *  the user to look up
     *
     * @return the user's principal
     */
    protected Principal createPrincipal(String username) {
        return this.createPrincipal(username, Status.UNAUTHORIZED);
    }

    /**
     * Creates a user principal using the caller's response status for unexpected user service failures
     *
     * @param username
     *  the user to look up
     * @param unexpectedFailureStatus
     *  the response status for unexpected user service failures
     *
     * @return the user's principal
     */
    protected Principal createPrincipal(String username, Status unexpectedFailureStatus) {
        UserInfo user;
        try {
            user = this.userServiceAdapter.findByLogin(username);
        }
        catch (UserServiceException e) {
            throw UserServiceExceptionTranslator.translate(e, this.i18nProvider.get(), log,
                unexpectedFailureStatus);
        }

        if (user == null) {
            throw new BadRequestException(this.i18nProvider.get().tr("User not found: {0}", username));
        }

        // TODO: This creates a lot of object churn. We should probably update this later in a way
        // that can do permission checking without creating piles of objects that we just throw away
        // without ever using them in the general case.
        return user.isSuperAdmin() != null && user.isSuperAdmin() ?
            (new UserPrincipal(username, null, true, user.getPrimaryOwner())) :
            (new UserPrincipal(username, this.permissionFactory.createPermissions(user), false,
                user.getPrimaryOwner()));
    }

}
