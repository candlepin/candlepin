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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import org.candlepin.exceptions.CandlepinException;
import org.candlepin.service.exception.subscription.SubscriptionActivationException;
import org.candlepin.service.exception.subscription.SubscriptionExhaustedTagException;
import org.candlepin.service.exception.subscription.SubscriptionExpiredTagException;
import org.candlepin.service.exception.subscription.SubscriptionInvalidTagException;
import org.candlepin.service.exception.subscription.SubscriptionServiceException;

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

import jakarta.ws.rs.core.Response.Status;

public class SubscriptionServiceExceptionTranslatorTest {
    private final I18n i18n = I18nFactory.getI18n(this.getClass(), Locale.US, I18nFactory.FALLBACK);

    private static Stream<Arguments> expectedFailures() {
        return Stream.of(
            Arguments.of(new SubscriptionActivationException("tag", "owner"),
                "No subscription was able to be activated with Dell service tag \"tag\"."),
            Arguments.of(new SubscriptionExhaustedTagException("tag", "owner"),
                "The Dell service tag \"tag\" has already been used to redeem a subscription."),
            Arguments.of(new SubscriptionExpiredTagException("tag", "owner"),
                "The Dell service tag \"tag\" is expired."),
            Arguments.of(new SubscriptionInvalidTagException("tag", "owner"),
                "The Dell service tag \"tag\" could not be used to retrieve a subscription."));
    }

    @ParameterizedTest
    @MethodSource("expectedFailures")
    public void testLogsExpectedFailuresWithoutStackTrace(SubscriptionServiceException failure, String message) {
        Logger logger = mock(Logger.class);

        CandlepinException result = SubscriptionServiceExceptionTranslator.translate(failure, this.i18n, logger);

        assertThat(result.httpReturnCode()).isEqualTo(Status.EXPECTATION_FAILED);
        assertThat(result.getMessage()).isEqualTo(message);
        assertThat(result.getCause()).isSameAs(failure);
        assertThat(result.isLogException()).isFalse();
        verify(logger).warn("{}", message);
        verifyNoMoreInteractions(logger);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "backend-secret")
    public void testLogsUnexpectedFailuresWithCauseAndSafeResponse(String serviceMessage) {
        Logger logger = mock(Logger.class);
        RuntimeException cause = new RuntimeException("nested-secret");
        SubscriptionServiceException failure = new SubscriptionServiceException(serviceMessage, cause);

        CandlepinException result = SubscriptionServiceExceptionTranslator.translate(failure, this.i18n, logger);

        assertThat(result.httpReturnCode()).isEqualTo(Status.EXPECTATION_FAILED);
        assertThat(result.message().getDisplayMessage()).isEqualTo("Error contacting subscription service");
        assertThat(result.getCause()).isSameAs(failure);
        assertThat(result.getCause().getCause()).isSameAs(cause);
        assertThat(result.isLogException()).isFalse();
        verify(logger).error("Unexpected error from Subscription Service: " + serviceMessage, failure);
        verifyNoMoreInteractions(logger);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "<invalid>"})
    public void testRetainsRejectionStatusForInvalidTags(String tag) {
        Logger logger = mock(Logger.class);
        SubscriptionInvalidTagException failure = new SubscriptionInvalidTagException(tag, "owner");

        CandlepinException result = SubscriptionServiceExceptionTranslator.translate(failure, this.i18n, logger);

        assertThat(result.httpReturnCode()).isEqualTo(Status.EXPECTATION_FAILED);
        assertThat(result.getMessage()).isEqualTo(
            "The Dell service tag \"" + tag + "\" could not be used to retrieve a subscription.");
        assertThat(result.getCause()).isSameAs(failure);
        verify(logger).warn("{}", result.getMessage());
        verifyNoMoreInteractions(logger);
    }

    @Test
    public void testRejectsMissingTranslationDependencies() {
        Logger logger = mock(Logger.class);
        SubscriptionServiceException failure = new SubscriptionServiceException();

        assertThrows(NullPointerException.class,
            () -> SubscriptionServiceExceptionTranslator.translate(null, this.i18n, logger));
        assertThrows(NullPointerException.class,
            () -> SubscriptionServiceExceptionTranslator.translate(failure, null, logger));
        assertThrows(NullPointerException.class,
            () -> SubscriptionServiceExceptionTranslator.translate(failure, this.i18n, null));
        verifyNoMoreInteractions(logger);
    }
}
