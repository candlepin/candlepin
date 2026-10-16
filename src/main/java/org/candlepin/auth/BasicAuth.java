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
import org.candlepin.exceptions.CandlepinException;
import org.candlepin.exceptions.NotAuthorizedException;
import org.candlepin.exceptions.ServiceUnavailableException;
import org.candlepin.resource.util.UserServiceExceptionTranslator;
import org.candlepin.resteasy.filter.AuthUtil;
import org.candlepin.service.UserServiceAdapter;
import org.candlepin.service.exception.user.UserServiceException;

import org.apache.commons.codec.binary.Base64;
import org.jboss.resteasy.spi.HttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.ws.rs.core.Response.Status;

/**
 * BasicAuth
 */
public class BasicAuth extends UserAuth {

    private static Logger log = LoggerFactory.getLogger(BasicAuth.class);

    @Inject
    BasicAuth(UserServiceAdapter userServiceAdapter, Provider<I18n> i18nProvider,
        PermissionFactory permissionFactory) {

        super(userServiceAdapter, i18nProvider, permissionFactory);
    }

    @Override
    public Principal getPrincipal(HttpRequest httpRequest) {
        try {
            String auth = AuthUtil.getHeader(httpRequest, "Authorization");

            if (auth != null && auth.toUpperCase().startsWith("BASIC ")) {
                String userpassEncoded = auth.substring(6);
                String[] userpass = new String(Base64
                    .decodeBase64(userpassEncoded)).split(":", 2);
                String username = userpass[0];
                String password = null;
                if (userpass.length > 1) {
                    password = userpass[1];
                }

                if (log.isDebugEnabled()) {
                    Integer length = (password == null) ? 0 : password.length();
                    log.debug("check for: {} - password of length {}", username, length);
                }

                if (userServiceAdapter.validateUser(username, password)) {
                    Principal principal = ((UserPrincipal) createPrincipal(username, Status.SERVICE_UNAVAILABLE))
                        .setAuthenticationMethod(AuthenticationMethod.BASIC);
                    log.debug("principal created for user '{}'", username);
                    return principal;
                }
                else {
                    throw new NotAuthorizedException(i18nProvider.get().tr("Invalid Credentials"));
                }
            }
        }
        catch (UserServiceException e) {
            throw hideAccountDetails(UserServiceExceptionTranslator.translate(e, this.i18nProvider.get(), log,
                Status.SERVICE_UNAVAILABLE));
        }
        catch (CandlepinException e) {
            if (log.isDebugEnabled()) {
                log.debug("Error getting principal " + e);
            }
            throw hideAccountDetails(e);
        }
        catch (Exception e) {
            if (log.isDebugEnabled()) {
                log.debug("Error getting principal " + e);
            }
            throw new ServiceUnavailableException(i18nProvider.get().tr("Error contacting user service"));
        }
        return null;
    }

    private CandlepinException hideAccountDetails(CandlepinException exception) {
        // This also covers lookup failures already translated by UserAuth.createPrincipal.
        if (exception instanceof NotAuthorizedException) {
            return new NotAuthorizedException(this.i18nProvider.get().tr("Invalid Credentials"), false);
        }

        return exception;
    }

}
