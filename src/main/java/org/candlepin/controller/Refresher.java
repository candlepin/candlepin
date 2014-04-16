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
package org.candlepin.controller;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Subscription;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.util.Util;

/**
 * Refresher
 */
public class Refresher {

    private CandlepinPoolManager poolManager;
    private SubscriptionServiceAdapter subAdapter;
    private boolean lazy;

    private Set<Owner> owners = Util.newSet();
    private Set<Product> products = Util.newSet();
    private Set<Subscription> subscriptions = Util.newSet();

    Refresher(CandlepinPoolManager poolManager, SubscriptionServiceAdapter subAdapter,
        boolean lazy) {
        this.poolManager = poolManager;
        this.subAdapter = subAdapter;
        this.lazy = lazy;
    }

    public Refresher add(Owner owner) {
        owners.add(owner);
        return this;
    }

    public Refresher add(Product product) {
        products.add(product);
        return this;
    }

    public Refresher add(Subscription subscription) {
        subscriptions.add(subscription);
        return this;
    }

    public void run() {
        Set<Entitlement> toRegen = new HashSet<Entitlement>();

        for (Product product : products) {
            subscriptions.addAll(subAdapter.getSubscriptions(product));
        }

        for (Subscription subscription : subscriptions) {
            // drop any subs for owners in our owners list. we'll get them with the full
            // refreshPools call.
            if (owners.contains(subscription.getOwner())) {
                continue;
            }

            /*
             * on the off chance that this is actually a new subscription, make the required
             * pools. this shouldn't happen; we should really get a refreshpools by owner
             * call for it, but why not handle it, just in case!
             */
            List<Pool> pools = poolManager.lookupBySubscriptionId(subscription.getId());
            poolManager.removeAndDeletePoolsOnOtherOwners(pools, subscription);
            if (pools.isEmpty()) {
                poolManager.createPoolsForSubscription(subscription);
            }
            else {
                toRegen.addAll(poolManager.updatePoolsForSubscription(
                    pools, subscription, true));
            }
        }

        for (Owner owner : owners) {
            toRegen.addAll(poolManager.refreshPoolsWithoutRegeneration(owner));
        }

        // now regenerate all pending entitlements
        poolManager.regenerateCertificatesOf(toRegen, lazy);
    }
}
