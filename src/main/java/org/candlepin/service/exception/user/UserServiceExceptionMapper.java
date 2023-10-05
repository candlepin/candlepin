/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
package org.candlepin.service.exception.user;

import org.candlepin.exceptions.CandlepinException;

import org.xnap.commons.i18n.I18n;

import javax.ws.rs.core.Response;



public class UserServiceExceptionMapper {
    private UserServiceExceptionMapper() { };

    public static void map(UserServiceException e, String username, I18n i18n) {
        if (e instanceof UserInvalidException) {
            throw new CandlepinException(null,
                i18n.tr("User '{0}' is not valid.", username));
        }
        else if (e instanceof UserMissingOwnerException) {
            throw new CandlepinException(null,
                i18n.tr("The system is unable to find an organization for user '{0}'", username));
        }
        else if (e instanceof UserMissingException) {
            throw new CandlepinException(null,
                i18n.tr("User '{0}' cannot be found.", username));
        }
        else if (e instanceof UserUnacceptedTermsException) {
            throw new CandlepinException(null,
                i18n.tr("You must first accept Red Hat''s Terms and conditions. " +
                    "Please visit {0} . You may have to log out of and back into the  " +
                    "Customer Portal in order to see the terms.",
                    "https://www.redhat.com/wapps/ugc"));
        }
        else if (e instanceof UserDisabledException) {
            throw new CandlepinException(null,
                i18n.tr("The user '{0}' has been disabled, if this is a mistake, " +
                    "please contact customer service.", username));
        }
        else if (e instanceof UserCredentialsException) {
            throw new CandlepinException(null,
                i18n.tr("Invalid username or password. To create a login, " +
                    "please visit {0}", "https://www.redhat.com/wapps/ugc/register.html"));
        }
        else if (e instanceof UserLoginNotFoundException) {
            throw new CandlepinException(Response.Status.fromStatusCode(e.getStatus()),
                i18n.tr("Unable to find user '{0}'", username));
        }
        else if (e instanceof UserUnknownValidateException) {
            throw new CandlepinException(Response.Status.fromStatusCode(e.getStatus()),
                i18n.tr("Unexpected error during user validation: {0}", e.getMessage()));
        }
        else if (e instanceof UserUnknownRetrievalException) {
            throw new CandlepinException(Response.Status.fromStatusCode(e.getStatus()),
                i18n.tr("Unexpected retrieval error from User Service: '{0}'", username));
        }
        else {
            throw new CandlepinException(Response.Status.fromStatusCode(e.getStatus()),
                i18n.tr("Unexpected error from User Service: {0}", e.getMessage()));
        }
    }
}
