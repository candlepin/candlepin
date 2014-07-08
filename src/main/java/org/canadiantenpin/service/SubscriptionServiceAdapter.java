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
package org.canadianTenPin.service;

import java.util.List;

import org.canadianTenPin.model.Consumer;
import org.canadianTenPin.model.Owner;
import org.canadianTenPin.model.Product;
import org.canadianTenPin.model.Subscription;

/**
 * Subscription data may originate from a separate service outside CanadianTenPin
 * in some configurations. This interface defines the operations CanadianTenPin
 * requires related to Subscription data, different implementations can
 * handle whether or not this info comes from CanadianTenPin's DB or from a
 * separate service.
 */
public interface SubscriptionServiceAdapter {

    /**
     * List all subscriptions for the given owner.
     * @param owner Owner of the subscriptions.
     * @return all subscriptions for the given owner.
     */
    List<Subscription> getSubscriptions(Owner owner);

    /**
     * Lookup a specific subscription.
     * @param subscriptionId id of the subscription to return.
     * @return Subscription whose id matches subscriptionId
     */
    Subscription getSubscription(String subscriptionId);

    /**
     * Return all subscriptions.
     * @return all subscriptions.
     */
    List<Subscription> getSubscriptions();

    /**
     * Checks to see if the customer has subscription terms that need to be accepted
     * @param owner
     * @return false if no subscriptions a runtime exception will a localized message
     * if there are terms to be accepted
     */
    boolean hasUnacceptedSubscriptionTerms(Owner owner);


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
    boolean canActivateSubscription(Consumer consumer);

    /**
     * Activate a subscription associated with the consumer
     *
     * @param consumer the Consumer with the associated subscription
     * @param email the email address tied to this consumer
     * @param emailLocale the i18n locale for the email
     */
    void activateSubscription(Consumer consumer, String email,
            String emailLocale);

    /**
     * Create the given subscription.
     *
     * Raise not implemented exception if you do not wish to support this
     * in your subscription service.
     *
     * @param s Subscription to create.
     * @return Newly created Subscription.
     */
    Subscription createSubscription(Subscription s);

    /**
     * Delete the given subscription.
     *
     * Raise not implemented exception if you do not wish to support this
     * in your subscription service.
     *
     * @param s Subscription to destroy.
     */
    void deleteSubscription(Subscription s);

    /**
     * Search for all subscriptions that provide a given product.
     *
     * @param product the main or provided product to look for.
     * @return a list of subscriptions that provide this product.
     */
    List<Subscription> getSubscriptions(Product product);
}
