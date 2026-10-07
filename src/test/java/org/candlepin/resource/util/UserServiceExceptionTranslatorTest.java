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

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.candlepin.exceptions.CandlepinException;
import org.candlepin.exceptions.ExceptionMessage;
import org.candlepin.exceptions.mappers.RuntimeExceptionMapper;
import org.candlepin.service.exception.user.UserDisabledException;
import org.candlepin.service.exception.user.UserInvalidException;
import org.candlepin.service.exception.user.UserServiceException;
import org.candlepin.service.exception.user.UserUnacceptedTermsException;
import org.candlepin.service.exception.user.UserUnauthorizedException;
import org.candlepin.util.ObjectMapperFactory;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.Logger;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Locale;
import java.util.stream.Stream;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.Response.Status;

class UserServiceExceptionTranslatorTest {
    private final I18n i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);

    private static Stream<Arguments> expectedFailures() {
        return Stream.of(
            Arguments.of(new UserInvalidException("alice"), "User \"alice\" is not valid."),
            Arguments.of(new UserDisabledException("alice"),
                "The user \"alice\" has been disabled, if this is a mistake, please contact customer service."),
            Arguments.of(new UserUnacceptedTermsException("alice"),
                "You must first accept Red Hat's Terms and conditions. Please visit https://www.redhat.com/wapps/ugc " +
                ". You may have to log out of and back into the  Customer Portal in order to see the terms."),
            Arguments.of(new UserUnauthorizedException("alice"),
                "Invalid username or password. To create a login, please visit " +
                "https://www.redhat.com/wapps/ugc/register.html"));
    }

    @ParameterizedTest
    @MethodSource("expectedFailures")
    void testLogsExpectedFailuresWithoutStackTrace(UserServiceException failure, String message) {
        Logger logger = mock(Logger.class);

        CandlepinException result = UserServiceExceptionTranslator.translate(failure, this.i18n, logger);

        assertThat(result)
            .returns(Status.UNAUTHORIZED, CandlepinException::httpReturnCode)
            .returns(message, CandlepinException::getMessage)
            .returns(false, CandlepinException::isLogException);
        verify(logger).warn("{}", message);
        verifyNoMoreInteractions(logger);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "backend-secret")
    void testLogsUnexpectedFailuresWithCauseAndUnauthorizedStatus(String serviceMessage) {
        Logger logger = mock(Logger.class);
        RuntimeException cause = new RuntimeException("nested-secret");
        UserServiceException failure = new UserServiceException(serviceMessage, cause);

        CandlepinException result = UserServiceExceptionTranslator.translate(failure, this.i18n, logger);

        assertThat(result)
            .returns(Status.UNAUTHORIZED, CandlepinException::httpReturnCode)
            .returns("Error contacting user service", CandlepinException::getMessage)
            .returns(false, CandlepinException::isLogException);
        assertThat(result.headers()).doesNotContainKey("WWW-Authenticate");
        assertThat(result.getCause()).isSameAs(failure);
        assertThat(result.getCause().getCause()).isSameAs(cause);
        RuntimeExceptionMapper mapper = spy(new RuntimeExceptionMapper());
        doReturn(MediaType.APPLICATION_JSON_TYPE).when(mapper).determineBestMediaType();
        try (Response response = mapper.toResponse(result)) {
            assertThat(response.getStatus()).isEqualTo(401);
            assertThat(response.getHeaderString("WWW-Authenticate")).isNull();
            assertThat(((ExceptionMessage) response.getEntity()).getDisplayMessage())
                .isEqualTo("Error contacting user service");
            assertThat(ObjectMapperFactory.getObjectMapper().writeValueAsString(response.getEntity()))
                .doesNotContain("backend-secret", "nested-secret");
        }

        verify(logger).error(this.i18n.tr("Unexpected error from User Service: {0}", serviceMessage), failure);
        verifyNoMoreInteractions(logger);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "<invalid>"})
    void testRetainsUnauthorizedStatusForInvalidUsernames(String username) {
        Logger logger = mock(Logger.class);

        CandlepinException result = UserServiceExceptionTranslator.translate(new UserInvalidException(username),
            this.i18n, logger);

        assertThat(result.httpReturnCode()).isEqualTo(Status.UNAUTHORIZED);
        verify(logger).warn("{}", result.getMessage());
        verifyNoMoreInteractions(logger);
    }

    @Test
    void testRejectsMissingTranslationDependencies() {
        Logger logger = mock(Logger.class);
        UserServiceException failure = new UserServiceException();

        assertThrows(NullPointerException.class,
            () -> UserServiceExceptionTranslator.translate(null, this.i18n, logger));
        assertThrows(NullPointerException.class,
            () -> UserServiceExceptionTranslator.translate(failure, null, logger));
        assertThrows(NullPointerException.class,
            () -> UserServiceExceptionTranslator.translate(failure, this.i18n, null));
        assertThrows(NullPointerException.class,
            () -> UserServiceExceptionTranslator.translate(failure, this.i18n, logger, null));
        verifyNoMoreInteractions(logger);
    }
}
