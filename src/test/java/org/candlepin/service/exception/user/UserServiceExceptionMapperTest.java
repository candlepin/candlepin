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

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.candlepin.exceptions.CandlepinException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Locale;
import java.util.stream.Stream;


public class UserServiceExceptionMapperTest {

    private static final String USERNAME = "test_user";
    private static final I18n I18N = I18nFactory.getI18n(UserServiceExceptionMapperTest.class,
        Locale.US, I18nFactory.FALLBACK);

    public static Stream<Arguments> exceptionMappings() {
        String message = "oops";
        return Stream.of(
            Arguments.of(new UserInvalidException(),
                I18N.tr("User '{0}' is not valid.", USERNAME)),
            Arguments.of(new UserUnacceptedTermsException(),
                I18N.tr("You must first accept Red Hat''s Terms and conditions. " +
                        "Please visit {0} . You may have to log out of and back into the  " +
                        "Customer Portal in order to see the terms.",
                    "https://www.redhat.com/wapps/ugc")),
            Arguments.of(new UserDisabledException(),
                I18N.tr("The user '{0}' has been disabled, if this is a mistake, " +
                    "please contact customer service.", USERNAME)),
            Arguments.of(new UserUnauthorizedException(),
                I18N.tr("Invalid username or password. To create a login, please visit {0}",
                    "https://www.redhat.com/wapps/ugc/register.html")),
            Arguments.of(new UserInvalidException(),
                I18N.tr("User '{0}' is not valid.", USERNAME)),
            Arguments.of(new UserServiceException(message),
                I18N.tr("Unexpected error from User Service: {0}", message))
        );
    }

    @ParameterizedTest
    @MethodSource("exceptionMappings")
    public void exceptionMappingTest(UserServiceException exceptionThrown, String expectedMessage) {
        assertThatThrownBy(() -> UserServiceExceptionMapper.map(exceptionThrown, USERNAME, I18N))
            .isInstanceOf(CandlepinException.class)
            .hasMessage(expectedMessage);
    }
}
