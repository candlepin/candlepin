/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.service;

import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Subscription;

import java.util.Date;
import java.util.List;

/**
 * Subscription data may originate from a separate service outside Candlepin
 * in some configurations. This interface defines the operations Candlepin
 * requires related to Subscription data, different implementations can
 * handle whether or not this info comes from Candlepin's DB or from a
 * separate service.
 */
public interface SubscriptionServiceAdapter {

    /**
     * List all subscriptions for the given owner and product, which have
     * changed or been created since the given date.
     * @param owner Owner of the subscriptions.
     * @param productId product id filter.
     * @param sinceDate changed since or created since date.
     * @return all subscriptions for the given owner and product, which have
     * changed or been created since the given date.
     */
    List<Subscription> getSubscriptionsSince(Owner owner, String productId,
            Date sinceDate);

    /**
     * List all subscriptions for the given owner and product.
     * @param owner Owner of the subscriptions.
     * @param productId product id filter.
     * @return all subscriptions for the given owner and product.
     */
    List<Subscription> getSubscriptions(Owner owner, String productId);
    
    /**
     * List all subscriptions which have been changed or created since the
     * given date.
     * @param sinceDate changed since or created since date.
     * @return all subscriptions which have been changed since the
     * given date.
     */
    List<Subscription> getSubscriptionsSince(Date sinceDate);

    /**
     * Return a subscription for the given token.
     * @param token token for subscription.
     * @return a subscription for the given token.
     */
    Subscription getSubscriptionForToken(String token);
    
    /**
     * Lookup a specific subscription.
     * @param subscriptionId id of the subscription to return.
     * @return Subscription whose id matches subscriptionId
     */
    Subscription getSubscription(Long subscriptionId);
}
