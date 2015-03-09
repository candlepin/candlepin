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

import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.Subscription;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.util.Util;

import com.google.inject.persist.Transactional;
import com.google.inject.persist.UnitOfWork;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Refresher
 */
public class Refresher {

    private CandlepinPoolManager poolManager;
    private SubscriptionServiceAdapter subAdapter;
    private boolean lazy;
    private UnitOfWork uow;

    private Set<Owner> owners = Util.newSet();
    private Set<Product> products = Util.newSet();

    Refresher(CandlepinPoolManager poolManager, SubscriptionServiceAdapter subAdapter,
        boolean lazy) {
        this.poolManager = poolManager;
        this.subAdapter = subAdapter;
        this.lazy = lazy;
    }

    public Refresher setUnitOfWork(UnitOfWork uow) {
        this.uow = uow;
        return this;
    }

    public Refresher add(Owner owner) {
        owners.add(owner);
        return this;
    }

    /**
     * Add a product that has been changed to be refreshed globally.
     *
     * Will be used to lookup any subscription using the product, either as a SKU or a
     * provided product, and trigger a refresh for that specific subscription.
     *
     * WARNING: Should only be used in upstream production environments, downstream should
     * always be driven by manifest import, which should never trigger a global refresh
     * for other orgs.
     *
     * @param product
     * @return
     */
    public Refresher add(Product product) {
        products.add(product);
        return this;
    }

    public void run() {

        // If products were specified on the refresher, lookup any subscriptions
        // using them, regardless of organization, and trigger a refresh for those
        // specific subscriptions.
        Set<Subscription> subscriptions = Util.newSet();
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
            refreshPoolsForSubscription(subscription, pools);
        }

        for (Owner owner : owners) {
            poolManager.refreshPoolsWithRegeneration(subAdapter, owner, lazy);
        }
    }

    // TODO: Can this be replaced with CandlepinPoolManager.refreshForSub?
    // They look identical... Suspect this is a holdover from some attempted transaction
    // magic.
    @Transactional
    private void refreshPoolsForSubscription(Subscription subscription, List<Pool> pools) {
        poolManager.removeAndDeletePoolsOnOtherOwners(subAdapter, pools, subscription);

        poolManager.createPoolsForSubscription(subscription, pools);
        // Regenerate certificates here, that way if it fails, the whole thing rolls back.
        // We don't want to refresh without marking ents dirty, they will never get regenerated
        poolManager.regenerateCertificatesByEntIds(subAdapter, poolManager.updatePoolsForSubscription(
            subAdapter, pools, subscription, true, new HashSet<Product>()), lazy);
    }
}
