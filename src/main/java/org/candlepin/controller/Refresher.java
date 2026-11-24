/*
 * Copyright (c) 2009 - 2025 Red Hat, Inc.
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

import org.candlepin.controller.util.ExpectedExceptionRetryWrapper;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolCurator;
import org.candlepin.model.Product;
import org.candlepin.service.SubscriptionServiceAdapter;
import org.candlepin.service.exception.product.ProductServiceException;
import org.candlepin.service.exception.subscription.SubscriptionServiceException;
import org.candlepin.service.model.OwnerInfo;
import org.candlepin.service.model.SubscriptionInfo;

import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.LockAcquisitionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;




public class Refresher {
    private static final Logger log = LoggerFactory.getLogger(Refresher.class);

    /**
     * The number of times to retry the refresh operation if it fails as a result of a constraint
     * violation.
     */
    private static final int CONSTRAINT_VIOLATION_RETRIES = 4;

    private final PoolManager poolManager;
    private final SubscriptionServiceAdapter subAdapter;
    private final OwnerCurator ownerCurator;
    private final PoolCurator poolCurator;
    private final PoolConverter poolConverter;

    private final Map<String, Owner> owners = new HashMap<>();
    private final Set<Product> products = new HashSet<>();
    private boolean lazy;

    public Refresher(PoolManager poolManager, SubscriptionServiceAdapter subAdapter,
        OwnerCurator ownerCurator, PoolCurator poolCurator, PoolConverter poolConverter) {

        this.poolManager = Objects.requireNonNull(poolManager);
        this.subAdapter = Objects.requireNonNull(subAdapter);
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
        this.poolCurator = Objects.requireNonNull(poolCurator);
        this.poolConverter = Objects.requireNonNull(poolConverter);

        this.lazy = true;
    }

    /**
     * Sets whether or not to perform lazy entitlement certificate regeneration when affected pools
     * are updated as a result of this refresher operation.
     *
     * @param lazy
     *  whether or not to perform lazy certificate regeneration
     *
     * @return
     *  a reference to this refresher
     */
    public Refresher setLazyCertificateRegeneration(boolean lazy) {
        this.lazy = lazy;
        return this;
    }

    /**
     * @return true if the refresher should perform lazy entitlement certificate regeneration or false if the
     *  refresher should regenerate entitlement certificates immediately.
     */
    public boolean getLazyCertificateRegeneration() {
        return this.lazy;
    }

    /**
     * Adds the {@link Owner} to list of owners that should be refreshed.
     *
     * @param owner
     *  that should be refreshed
     *
     * @throws IllegalArgumentException
     *  if the provided owner is null or has a null key
     *
     * @return a reference to this refresher
     */
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
     *  the product that should be refreshed globally
     *
     * @return this Refresher instance
     */
    public Refresher add(Product product) {
        products.add(product);
        return this;
    }

    public void run() {
        // This call to refreshProducts is commented out because it is currently a dead code branch.
        // If we are to use product/subscription specific refreshes in the future, we can uncomment this
        // method invocation.
        // this.refreshProducts();

        for (Owner owner : this.owners.values()) {
            Runnable refreshTask = () -> {
                try {
                    this.poolManager.refreshPoolsWithRegeneration(this.subAdapter, owner, this.lazy);
                    this.recalculatePoolQuantitiesForOwner(owner);
                    this.updateRefreshDate(owner);
                }
                catch (SubscriptionServiceException e) {
                    String msg = "Unexpected subscription error for organization '%s'";
                    throw new RuntimeException(String.format(msg, owner.getKey()), e);
                }
                catch (ProductServiceException e) {
                    throw new RuntimeException("Unexpected product error in pool refresh", e);
                }
            };

            // Retry this operation if we hit a unique constraint violation (two orgs creating the
            // same products or content in simultaneous transactions), or we deadlock (same deal,
            // but in a spicy entity order).
            new ExpectedExceptionRetryWrapper()
                .addException(ConstraintViolationException.class)
                .addException(LockAcquisitionException.class)
                .retries(CONSTRAINT_VIOLATION_RETRIES)
                .execute(() -> this.poolCurator.transactional()
                    .allowExistingTransactions()
                    .execute(refreshTask));
        }
    }

    private void refreshProducts() {
        // If products were specified on the refresher, lookup any subscriptions
        // using them, regardless of organization, and trigger a refresh for those
        // specific subscriptions.
        Set<SubscriptionInfo> subscriptions = new HashSet<>();
        for (Product product : products) {
            // TODO: This adapter call is not implemented in prod, and cannot be. We plan
            // to fix this whole code path in near future by looking for pools using the
            // given products to be refreshed.
            Collection<? extends SubscriptionInfo> subs = new ArrayList<>();
            try {
                subs = subAdapter.getSubscriptionsByProductId(product.getId());
            }
            catch (SubscriptionServiceException e) {
                log.error("Unable to retrieve subscriptions by product id '{}'", product.getId());
                throw new RuntimeException(
                    String.format("Unable to retrieve subscriptions by product id '%s'", product.getId()),
                    e);
            }

            log.debug("Will refresh {} subscriptions in all orgs using product: {}",
                subs.size(), product.getId());

            if (log.isDebugEnabled()) {
                for (SubscriptionInfo s : subs) {
                    OwnerInfo so = s.getOwner();

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

        for (SubscriptionInfo subscription : subscriptions) {
            // drop any subs for owners in our owners list. we'll get them with the full
            // refreshPools call.
            OwnerInfo so = subscription.getOwner();

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
            Pool primaryPool = this.poolConverter.convertToPrimaryPool(subscription);
            poolManager.refreshPoolsForPrimaryPool(primaryPool, true, lazy, Collections.emptyMap());
        }
    }

    private Owner updateRefreshDate(Owner owner) {
        owner.setLastRefreshed(new Date());
        return this.ownerCurator.merge(owner);
    }

    private void recalculatePoolQuantitiesForOwner(Owner owner) {
        this.poolCurator.calculateConsumedForOwnersPools(owner);
        this.poolCurator.calculateExportedForOwnersPools(owner);

        log.info("Successfully recalculated quantities for owner: {}", owner.getKey());
    }

}
