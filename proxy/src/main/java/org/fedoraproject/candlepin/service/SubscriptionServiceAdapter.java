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

import java.util.List;

import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Subscription;

/**
 * Subscription data may originate from a separate service outside Candlepin
 * in some configurations. This interface defines the operations Candlepin requires
 * related to Subscription data, different implementations can handle whether or not
 * this info comes from Candlepin's DB or from a separate service.
 */
public interface SubscriptionServiceAdapter {

    /**
     * List all Subscriptions for the given owner and product ID.
     * @param owner Owner.
     * @param productId Product OID or SKU. (not clear yet)
     * @return
     */
    public abstract List<Subscription> getSubscriptions(Owner owner, String productId);
    
    /**
     * Query a specific subscription.
     * @param owner
     * @param subscriptionId
     * @return
     */
    // TODO: Is owner required here?
    public abstract Subscription getSubscription(Owner owner, Long subscriptionId);

}
