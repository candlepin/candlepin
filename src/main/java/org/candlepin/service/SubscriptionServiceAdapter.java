/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.service;

import org.candlepin.service.model.ConsumerInfo;
import org.candlepin.service.model.SubscriptionInfo;

import java.util.Collection;



/**
 * Subscription data may originate from a separate service outside Candlepin
 * in some configurations. This interface defines the operations Candlepin
 * requires related to Subscription data, different implementations can
 * handle whether or not this info comes from Candlepin's DB or from a
 * separate service.
 */
public interface SubscriptionServiceAdapter {

    /**
     * Return all subscriptions.
     * @return all subscriptions.
     */
    Collection<? extends SubscriptionInfo> getSubscriptions();

    /**
     * Lookup a specific subscription.
     * @param subscriptionId id of the subscription to return.
     * @return Subscription whose id matches subscriptionId
     */
    SubscriptionInfo getSubscription(String subscriptionId);

    /**
     * List all subscriptions for the given owner.
     * @param ownerKey Owner of the subscriptions.
     * @return all subscriptions for the given owner.
     */
    Collection<? extends SubscriptionInfo> getSubscriptions(String ownerKey);

    /**
     * List all active subscription ids for the given owner.
     * @param ownerKey Owner of the subscriptions.
     * @return ids of all subscriptions for the given owner.
     */
    Collection<String> getSubscriptionIds(String ownerKey);

    /**
     * Search for all subscriptions that provide a given product.
     *
     * @param productId the main or provided product to look for.
     * @return a list of subscriptions that provide this product.
     */
    Collection<? extends SubscriptionInfo> getSubscriptionsByProductId(String productId);

    /**
     * Checks to see if the customer has subscription terms that need to be accepted
     * @param ownerKey
     * @return false if no subscriptions a runtime exception will a localized message
     * if there are terms to be accepted
     */
    boolean hasUnacceptedSubscriptionTerms(String ownerKey);

    /**
     * A pool for a subscription id has been created. Send the activation email
     * if necessary
     *
     * @param subscriptionId
     */
    void sendActivationEmail(String subscriptionId);

    /**
     * Can this consumer activate a subscription?
     *
     * @param consumer
     * @return <code>true</code> if and only if this consumer can activate a subscription
     */
    boolean canActivateSubscription(ConsumerInfo consumer);

    /**
     * Activate a subscription associated with the consumer
     *
     * @param consumer the Consumer with the associated subscription
     * @param email the email address tied to this consumer
     * @param emailLocale the i18n locale for the email
     */
    void activateSubscription(ConsumerInfo consumer, String email, String emailLocale);

    /**
     * Some subscription services are read-only. This allows us to avoid certain
     * costly operations when we cannot do anything with them. For example,
     * cleanupExpiredPools will also try to get and then delete the source
     * subscription, however the prior is not necessary when the latter is a
     * no-op.
     *
     * @return Whether or not this service is read-only
     */
    boolean isReadOnly();
}
