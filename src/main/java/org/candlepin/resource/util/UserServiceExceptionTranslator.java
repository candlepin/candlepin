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
package org.candlepin.resource.util;

import org.candlepin.exceptions.CandlepinException;
import org.candlepin.exceptions.NotAuthorizedException;
import org.candlepin.service.exception.user.UserDisabledException;
import org.candlepin.service.exception.user.UserInvalidException;
import org.candlepin.service.exception.user.UserServiceException;
import org.candlepin.service.exception.user.UserUnacceptedTermsException;
import org.candlepin.service.exception.user.UserUnauthorizedException;

import org.slf4j.Logger;
import org.xnap.commons.i18n.I18n;

import java.util.Objects;

import jakarta.ws.rs.core.Response.Status;

/**
 * Translation and logging of user service failures as authentication rejections or service outages
 */
public final class UserServiceExceptionTranslator {
    private UserServiceExceptionTranslator() {
        // Utility class.
    }

    /**
     * Translates a user service failure as an unauthorized response,
     * logging expected rejections without a stack trace
     *
     * @param exception
     *  the service failure
     * @param i18n
     *  the caller's message translator
     * @param logger
     *  the caller's logger
     *
     * @throws NullPointerException
     *  if exception, i18n or logger is null
     *
     * @return the translated exception to throw
     */
    public static CandlepinException translate(UserServiceException exception, I18n i18n, Logger logger) {
        return UserServiceExceptionTranslator.translate(exception, i18n, logger, Status.UNAUTHORIZED);
    }

    /**
     * Translates a user service failure, using the caller's status for unexpected failures
     *
     * @param exception
     *  the service failure
     * @param i18n
     *  the caller's message translator
     * @param logger
     *  the caller's logger
     * @param unexpectedFailureStatus
     *  the response status for unexpected failures; expected rejections always return unauthorized
     *
     * @throws NullPointerException
     *  if exception, i18n, logger or unexpectedFailureStatus is null
     *
     * @return the translated exception to throw
     */
    public static CandlepinException translate(UserServiceException exception, I18n i18n, Logger logger,
        Status unexpectedFailureStatus) {

        Objects.requireNonNull(exception, "exception is null");
        Objects.requireNonNull(i18n, "i18n is null");
        Objects.requireNonNull(logger, "logger is null");
        Objects.requireNonNull(unexpectedFailureStatus, "unexpectedFailureStatus is null");

        String message;
        switch (exception) {
            case UserInvalidException invalid -> message = i18n.tr("User \"{0}\" is not valid.", invalid.getUsername());
            case UserUnacceptedTermsException _ ->
                message = i18n.tr("You must first accept Red Hat''s Terms and conditions. " +
                    "Please visit {0} . You may have to log out of and back into the  " +
                    "Customer Portal in order to see the terms.", "https://www.redhat.com/wapps/ugc");
            case UserDisabledException disabled ->
                message = i18n.tr("The user \"{0}\" has been disabled, if this is a mistake, " +
                    "please contact customer service.", disabled.getUsername());
            case UserUnauthorizedException _ ->
                message = i18n.tr("Invalid username or password. To create a login, " +
                    "please visit {0}", "https://www.redhat.com/wapps/ugc/register.html");
            default -> {
                message = i18n.tr("Unexpected error from User Service: {0}", exception.getMessage());
                logger.error(message, exception);
                return new CandlepinException(unexpectedFailureStatus,
                    i18n.tr("Error contacting user service"), false, exception);
            }
        }

        logger.warn("{}", message);
        return new NotAuthorizedException(message, false);
    }
}
