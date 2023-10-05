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
        if (e instanceof SubscriptionMissingTagException) {
            message = i18n.tr(
                "A subscription was not found for the given Dell service tag: {0}",
                dellAssetTag);
        }
        else if (e instanceof SubscriptionExpiredTagException) {
            message = i18n.tr("The Dell service tag: {0}, is expired", dellAssetTag);
        }
        else if (e instanceof SubscriptionActivationNoAdminsException) {
            message = i18n.tr("No admins were returned for owner {0}", ownerKey);
        }
        else if (e instanceof SubscriptionUnknownActivationException) {
            message = i18n.tr(
                "The system is unable to redeem the requested subscription: {0}", dellAssetTag);
        }
        else if (e instanceof SubscriptionUsedTagException) {
            message = i18n.tr(
                "The Dell service tag: {0}, has already been used to redeem a subscription",
                dellAssetTag);
        }
        else if (e instanceof SubscriptionActivationNoTagException) {
            message = i18n.tr(
                "No subscription was activated because there is no Dell Asset Tag",
                dellAssetTag);
        }
        else if (e instanceof SubscriptionActivationNoRedemptionTagException) {
            message = i18n.tr(
                "The system is unable to redeem the requested subscription: {0}", dellAssetTag);
        }
        else if (e instanceof SubscriptionNotFoundTagException) {
            message = i18n.tr("The Dell service tag: {0}, could not be found.", dellAssetTag);
        }
        else {
            // Its some other kind of subscription service exception
            message = i18n.tr(
                "Unexpected error from Subscription Service: {0}", e.getMessage());
        }
        return message;
    }
}
