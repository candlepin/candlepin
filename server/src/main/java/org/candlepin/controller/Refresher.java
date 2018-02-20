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
import org.candlepin.model.dto.Subscription;
import org.candlepin.service.OwnerServiceAdapter;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.util.Util;

import com.google.inject.persist.UnitOfWork;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;



/**
 * Refresher
 */
public class Refresher {

    private CandlepinPoolManager poolManager;
    private SubscriptionServiceAdapter subAdapter;
    private OwnerServiceAdapter ownerAdapter;
    private OwnerManager ownerManager;
    private boolean lazy;
    private UnitOfWork uow;
    private static Logger log = LoggerFactory.getLogger(Refresher.class);

    private Map<String, Owner> owners = Util.newMap();
    private Set<Product> products = Util.newSet();

    Refresher(CandlepinPoolManager poolManager, SubscriptionServiceAdapter subAdapter,
        OwnerServiceAdapter ownerAdapter, OwnerManager ownerManager, boolean lazy) {

        this.poolManager = poolManager;
        this.subAdapter = subAdapter;
        this.ownerAdapter = ownerAdapter;
        this.ownerManager = ownerManager;
        this.lazy = lazy;
    }

    public Refresher setUnitOfWork(UnitOfWork uow) {
        this.uow = uow;
        return this;
    }

    public Refresher add(Owner owner) {
        if (owner == null || owner.getKey() == null) {
            throw new IllegalArgumentException("Owner is null or lacks identifying information");
        }

        this.owners.put(owner.getKey(), owner);
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
     * @return this Refresher instance
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
            // TODO: This adapter call is not implemented in prod, and cannot be. We plan
            // to fix this whole code path in near future by looking for pools using the
            // given products to be refreshed.
            List<Subscription> subs = subAdapter.getSubscriptions(product.toDTO());
            log.debug("Will refresh {} subscriptions in all orgs using product: ",
                subs.size(), product.getId());

            if (log.isDebugEnabled()) {
                for (Subscription s : subs) {
                    Owner so = s.getOwner();

                    if (so == null || so.getKey() == null) {
                        log.debug("  Received a subscription without a well-defined owner: {}", s.getId());
                        continue;
                    }

                    if (!this.owners.containsKey(so.getKey())) {
                        log.debug("    {}", s);
                    }
                }
            }

            subscriptions.addAll(subs);
        }

        for (Subscription subscription : subscriptions) {
            // drop any subs for owners in our owners list. we'll get them with the full
            // refreshPools call.
            Owner so = subscription.getOwner();

            // This probably shouldn't ever happen, but let's make sure it doesn't anyway.
            if (so == null || so.getKey() == null) {
                log.error("Received a subscription without a well-defined owner: {}", subscription.getId());
                continue;
            }

            if (this.owners.containsKey(so.getKey())) {
                log.debug("Skipping subscription \"{}\" for owner: {}", subscription.getId(), so);
                continue;
            }

            /*
             * on the off chance that this is actually a new subscription, make
             * the required pools. this shouldn't happen; we should really get a
             * refresh pools by owner call for it, but why not handle it, just
             * in case!
             *
             * Regenerate certificates here, that way if it fails, the whole
             * thing rolls back. We don't want to refresh without marking ents
             * dirty, they will never get regenerated
             */
            Pool masterPool = poolManager.convertToMasterPool(subscription);
            poolManager.refreshPoolsForMasterPool(masterPool, true, lazy,
                Collections.<String, Product>emptyMap(), false);
        }

        for (Owner owner : this.owners.values()) {
            poolManager.refreshPoolsWithRegeneration(this.subAdapter, owner, this.lazy);
            poolManager.recalculatePoolQuantitiesForOwner(owner);

            ownerManager.refreshContentAccessMode(this.ownerAdapter, owner);
            ownerManager.updateRefreshDate(owner);
        }
    }

}
