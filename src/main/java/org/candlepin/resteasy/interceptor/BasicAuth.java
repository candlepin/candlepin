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
import org.candlepin.exceptions.CandlepinException;
import org.candlepin.exceptions.ServiceUnavailableException;
import org.candlepin.exceptions.UnauthorizedException;
import org.candlepin.service.UserServiceAdapter;

import com.google.inject.Inject;
import com.google.inject.Injector;

import org.apache.commons.codec.binary.Base64;
import org.jboss.resteasy.spi.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

/**
 * BasicAuth
 */
class BasicAuth extends UserAuth {

    private static Logger log = LoggerFactory.getLogger(BasicAuth.class);

    @Inject
    BasicAuth(UserServiceAdapter userServiceAdapter, Injector injector) {
        super(userServiceAdapter, injector);
    }

    @Override
    public Principal getPrincipal(HttpRequest request) {
        I18n i18n = injector.getInstance(I18n.class);
        try {
            String auth = AuthUtil.getHeader(request, "Authorization");

            if (auth != null && auth.toUpperCase().startsWith("BASIC ")) {
                String userpassEncoded = auth.substring(6);
                String[] userpass = new String(Base64
                    .decodeBase64(userpassEncoded)).split(":");

                String username = userpass[0];
                String password = null;
                if (userpass.length > 1) {
                    password = userpass[1];
                }

                log.debug("check for: " + username + " - password of length #" +
                    (password == null ? 0 : password.length()) + " = <omitted>");

                if (userServiceAdapter.validateUser(username, password)) {
                    Principal principal = createPrincipal(username);
                    if (log.isDebugEnabled()) {
                        log.debug("principal created for user '" + username);
                    }

                    return principal;
                }
                else {
                    throw new UnauthorizedException(i18n.tr("Invalid Credentials"));
                }
            }
        }
        catch (CandlepinException e) {
            if (log.isDebugEnabled()) {
                log.debug("Error getting principal " + e);
            }
            throw e;
        }
        catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Error getting principal " + e);
            }
            throw new ServiceUnavailableException(i18n
                .tr("Error contacting user service"));
        }
        return null;
    }

}
