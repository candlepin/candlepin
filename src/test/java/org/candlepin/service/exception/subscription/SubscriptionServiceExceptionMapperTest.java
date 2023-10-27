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
package org.candlepin.service.exception.subscription;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.xnap.commons.i18n.I18n;
import org.xnap.commons.i18n.I18nFactory;

import java.util.Locale;
import java.util.stream.Stream;


public class SubscriptionServiceExceptionMapperTest {

    private static final String SERVICE_TAG = "service_tag";
    private static final String OWNER_KEY = "owner_key";
    private static final I18n I18N = I18nFactory.getI18n(SubscriptionServiceExceptionMapperTest.class,
        Locale.US, I18nFactory.FALLBACK);

    public static Stream<Arguments> exceptionMappings() {
        String message = "oops";
        return Stream.of(
            Arguments.of(new SubscriptionActivationException(),
                I18N.tr("No subscription was able to be activated with Dell service tag '{0}'.",
                    OWNER_KEY)),
            Arguments.of(new SubscriptionExhaustedTagException(),
                I18N.tr("The Dell service tag '{0}' has already been used to redeem a subscription.",
                    SERVICE_TAG)),
            Arguments.of(new SubscriptionExpiredTagException(),
                I18N.tr("The Dell service tag '{0}' is expired.",
                    SERVICE_TAG)),
            Arguments.of(new SubscriptionInvalidTagException(),
                I18N.tr("The Dell service tag '{0}' could not be used to retrieve a subscription.",
                    SERVICE_TAG)),
            Arguments.of(new SubscriptionServiceException(message),
                I18N.tr("Unexpected error from Subscription Service: {0}", message))
        );
    }

    @ParameterizedTest
    @MethodSource("exceptionMappings")
    public void exceptionMappingTest(SubscriptionServiceException exceptionThrown, String expectedMessage) {
        assertEquals(expectedMessage,
            SubscriptionServiceExceptionMapper.map(exceptionThrown, SERVICE_TAG, OWNER_KEY, I18N));
    }
}
