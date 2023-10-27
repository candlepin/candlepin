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

import org.xnap.commons.i18n.I18n;



public class SubscriptionServiceExceptionMapper {
    private SubscriptionServiceExceptionMapper() { };

    public static String map(SubscriptionServiceException e, String dellAssetTag, String ownerKey,
        I18n i18n) {
        String message = "";
        if (e instanceof SubscriptionActivationException) {
            message = i18n.tr(
                "No subscription was able to be activated with Dell service tag '{0}'.", dellAssetTag);
        }
        else if (e instanceof SubscriptionExhaustedTagException) {
            message = i18n.tr(
                "The Dell service tag '{0}' has already been used to redeem a subscription.", dellAssetTag);
        }
        else if (e instanceof SubscriptionExpiredTagException) {
            message = i18n.tr("The Dell service tag '{0}' is expired.", dellAssetTag);
        }
        else if (e instanceof SubscriptionInvalidTagException) {
            message = i18n.tr(
                "The Dell service tag '{0}' could not be used to retrieve a subscription.", dellAssetTag);
        }
        else {
            // Its some other kind of subscription service exception
            message = i18n.tr("Unexpected error from Subscription Service: {0}", e.getMessage());
        }
        return message;
    }
}
