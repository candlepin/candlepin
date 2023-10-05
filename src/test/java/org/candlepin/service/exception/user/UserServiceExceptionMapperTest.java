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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.candlepin.TestingModules;
import org.candlepin.exceptions.CandlepinException;

import com.google.inject.Guice;
import com.google.inject.Injector;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.xnap.commons.i18n.I18n;



public class UserServiceExceptionMapperTest {

    I18n i18n;

    @BeforeEach
    public void init() {
        Injector injector = Guice.createInjector(
            new TestingModules.ServletEnvironmentModule());
        i18n = injector.getInstance(I18n.class);
    }

    @Test
    public void exceptionMappingTest() {
        String username = "test_user";
        String message = "oops";
        assertEquals(i18n.tr("User '{0}' is not valid.", username),
            assertThrows(CandlepinException.class,
                () -> UserServiceExceptionMapper.map(new UserInvalidException(),
                    username, i18n))
                .getMessage());
        assertEquals(i18n.tr("The system is unable to find an organization for user '{0}'", username),
            assertThrows(CandlepinException.class,
                () -> UserServiceExceptionMapper.map(new UserMissingOwnerException(),
                    username, i18n))
                .getMessage());
        assertEquals(i18n.tr("User '{0}' cannot be found.", username),
            assertThrows(CandlepinException.class,
                () -> UserServiceExceptionMapper.map(new UserMissingException(),
                    username, i18n))
                .getMessage());
        assertEquals(i18n.tr("You must first accept Red Hat''s Terms and conditions. " +
            "Please visit {0} . You may have to log out of and back into the  " +
            "Customer Portal in order to see the terms.",
            "https://www.redhat.com/wapps/ugc"),
            assertThrows(CandlepinException.class,
                () -> UserServiceExceptionMapper.map(new UserUnacceptedTermsException(),
                    username, i18n))
                .getMessage());
        assertEquals(i18n.tr("The user '{0}' has been disabled, if this is a mistake, " +
            "please contact customer service.", username),
            assertThrows(CandlepinException.class,
                () -> UserServiceExceptionMapper.map(new UserDisabledException(),
                    username, i18n))
                .getMessage());
        assertEquals(i18n.tr("Invalid username or password. To create a login, " +
            "please visit {0}", "https://www.redhat.com/wapps/ugc/register.html"),
            assertThrows(CandlepinException.class,
                () -> UserServiceExceptionMapper.map(new UserCredentialsException(),
                    username, i18n))
                .getMessage());
        assertEquals(i18n.tr("Unable to find user '{0}'", username),
            assertThrows(CandlepinException.class,
                () -> UserServiceExceptionMapper.map(new UserLoginNotFoundException(),
                    username, i18n))
                .getMessage());
        assertEquals(i18n.tr("Unexpected error during user validation: {0}", message),
            assertThrows(CandlepinException.class,
                () -> UserServiceExceptionMapper.map(new UserUnknownValidateException(message),
                    username, i18n))
                .getMessage());
        assertEquals(i18n.tr("Unexpected retrieval error from User Service: '{0}'", username),
            assertThrows(CandlepinException.class,
                () -> UserServiceExceptionMapper.map(new UserUnknownRetrievalException(),
                    username, i18n))
                .getMessage());
        assertEquals(i18n.tr("Unexpected error from User Service: {0}", message),
            assertThrows(CandlepinException.class,
                () -> UserServiceExceptionMapper.map(new UserServiceException(message),
                    username, i18n))
                .getMessage());
    }
}
