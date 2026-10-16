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
import org.candlepin.service.exception.subscription.SubscriptionActivationException;
import org.candlepin.service.exception.subscription.SubscriptionExhaustedTagException;
import org.candlepin.service.exception.subscription.SubscriptionExpiredTagException;
import org.candlepin.service.exception.subscription.SubscriptionInvalidTagException;
import org.candlepin.service.exception.subscription.SubscriptionServiceException;

import org.slf4j.Logger;
import org.xnap.commons.i18n.I18n;

import java.util.Objects;

import jakarta.ws.rs.core.Response.Status;

/**
 * Translation and logging of subscription service failures for API callers
 */
public final class SubscriptionServiceExceptionTranslator {
    private SubscriptionServiceExceptionTranslator() {
        // Utility class.
    }

    /**
     * Translates a subscription service failure, logging expected rejections without a stack trace
     *
     * @param exception
     *  the service failure
     * @param i18n
     *  the caller's message translator
     * @param logger
     *  the caller's logger
     * @return the translated exception to throw
     */
    public static CandlepinException translate(SubscriptionServiceException exception, I18n i18n, Logger logger) {
        Objects.requireNonNull(exception, "exception is null");
        Objects.requireNonNull(i18n, "i18n is null");
        Objects.requireNonNull(logger, "logger is null");

        String message;
        switch (exception) {
            case SubscriptionActivationException activation ->
                message = i18n.tr("No subscription was able to be activated with Dell service tag \"{0}\".",
                    activation.getDellAssetTag());
            case SubscriptionExhaustedTagException exhausted -> message = i18n.tr(
                "The Dell service tag \"{0}\" has already been used to redeem a subscription.",
                exhausted.getDellAssetTag());
            case SubscriptionExpiredTagException expired ->
                message = i18n.tr("The Dell service tag \"{0}\" is expired.", expired.getDellAssetTag());
            case SubscriptionInvalidTagException invalid ->
                message = i18n.tr("The Dell service tag \"{0}\" could not be used to retrieve a subscription.",
                    invalid.getDellAssetTag());
            default -> {
                message = i18n.tr("Unexpected error from Subscription Service: {0}", exception.getMessage());
                logger.error(message, exception);
                return new CandlepinException(Status.EXPECTATION_FAILED,
                    i18n.tr("Error contacting subscription service"), false, exception);
            }
        }

        logger.warn("{}", message);
        return new CandlepinException(Status.EXPECTATION_FAILED, message, false, exception);
    }
}
