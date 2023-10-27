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
package org.candlepin.service.exception.cloudregistration;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.candlepin.exceptions.BadRequestException;
import org.candlepin.exceptions.CandlepinException;
import org.candlepin.exceptions.NotAuthorizedException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Locale;
import java.util.stream.Stream;


public class CloudRegistrationServiceExceptionMapperTest {

    private static final String TYPE = "test_type";
    private static final I18n I18N = I18nFactory.getI18n(CloudRegistrationServiceExceptionMapperTest.class,
        Locale.US, I18nFactory.FALLBACK);

    public static Stream<Arguments> exceptionMappings() {
        String message = "oops";
        return Stream.of(
            Arguments.of(NotAuthorizedException.class, new CloudRegistrationAuthorizationException(),
                I18N.tr("Cloud provider or account details could not be resolved to an organization")),
            Arguments.of(BadRequestException.class, new CloudRegistrationMalformedDataException(),
                I18N.tr("Unable to complete Cloud Registration with provided data")),
            Arguments.of(CandlepinException.class, new CloudRegistrationServiceException(message),
                I18N.tr("Unexpected error from Cloud Registration Service: {0}", message))
        );
    }

    @ParameterizedTest
    @MethodSource("exceptionMappings")
    public void exceptionMappingTest(Class<Exception> expectedExceptionType,
        CloudRegistrationServiceException exceptionToThrow, String expectedMessage) {
        assertThatThrownBy(() -> CloudRegistrationServiceExceptionMapper.map(exceptionToThrow, TYPE, I18N))
            .isInstanceOf(expectedExceptionType)
            .hasMessage(expectedMessage);
    }
}
