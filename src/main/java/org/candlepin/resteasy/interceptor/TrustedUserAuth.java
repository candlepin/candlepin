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
import org.candlepin.auth.TrustedUserPrincipal;
import org.candlepin.service.UserServiceAdapter;
import org.jboss.resteasy.spi.HttpRequest;

import com.google.inject.Inject;
import com.google.inject.Injector;

/**
 * This auth form allows for a consumer id to be passed in a clear http header.
 * This should be used only if the environment is known to be secure
 */
class TrustedUserAuth extends UserAuth {

    public static final String USER_HEADER = "cp-user";

    @Inject
    TrustedUserAuth(UserServiceAdapter userServiceAdaper, Injector injector) {
        super(userServiceAdaper, injector);
    }

    public Principal getPrincipal(HttpRequest request) {

        Principal principal = null;
        String username = AuthUtil.getHeader(request, USER_HEADER);

        if (username != null && username.length() > 0) {
            principal = new TrustedUserPrincipal(username);
        }

        return principal;
    }
}
