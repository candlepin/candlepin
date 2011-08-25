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
package org.fedoraproject.candlepin.controller;

import java.util.List;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.policy.EntitlementRefusedException;

/**
 *
 */
public interface PoolManager {

    Pool createPool(Pool p);

    /**
     * @param sub
     * @return the newly created Pool
     */
    List<Pool> createPoolsForSubscription(Subscription sub);

    /**
     * Cleanup entitlements and safely delete the given pool.
     *
     * @param pool
     */
    void deletePool(Pool pool);

    /**
     * Request an entitlement by pool..
     *
     * If the entitlement cannot be granted, null will be returned.
     *
     * TODO: Throw exception if entitlement not granted. Report why.
     *
     * @param consumer
     * consumer requesting to be entitled
     * @param pool
     * entitlement pool to consume from
     * @return Entitlement
     *
     * @throws EntitlementRefusedException if entitlement is refused
     */
    Entitlement entitleByPool(Consumer consumer, Pool pool, Integer quantity)
        throws EntitlementRefusedException;

    Entitlement entitleByProduct(Consumer consumer, String productId)
        throws EntitlementRefusedException;

    /**
     * Request an entitlement by product.
     *
     * If the entitlement cannot be granted, null will be returned.
     *
     * TODO: Throw exception if entitlement not granted. Report why.
     *
     * @param consumer
     * consumer requesting to be entitled
     * @param productIds
     * products to be entitled.
     * @return Entitlement
     * @throws EntitlementRefusedException if entitlement is refused
     */
    List<Entitlement> entitleByProducts(Consumer consumer, String[] productIds)
        throws EntitlementRefusedException;


    Pool find(String poolId);

    Iterable<Pool> getListOfEntitlementPoolsForProduct(String productId);

    Pool lookupBySubscriptionId(String id);

    /**
     * Check our underlying subscription service and update the pool data. Note
     * that refreshing the pools doesn't actually take any action, should a subscription
     * be reduced, expired, or revoked. Pre-existing entitlements will need to be dealt
     * with separately from this event.
     *
     * @param owner Owner to be refreshed.
     */
    void refreshPools(Owner owner);

    void regenerateCertificatesOf(Iterable<Entitlement> iterable);

    /**
     * @param e
     */
    void regenerateCertificatesOf(Entitlement e);

    void regenerateCertificatesOf(String productId);

    void regenerateEntitlementCertificates(Consumer consumer);

    void revokeAllEntitlements(Consumer consumer);

    void removeAllEntitlements(Consumer consumer);

    void revokeEntitlement(Entitlement entitlement);

    void removeEntitlement(Entitlement entitlement);


    /**
     * Update the given list of pools for a subscription. 
     * 
     * This method checks for change in quantity, dates, and products. 
     * 
     * @param existingPools the existing pools referencing this subscription
     * @param sub the subscription
     */
    void updatePoolsForSubscription(List<Pool> existingPools, Subscription sub);

    /**
     * Update the given pool for a subscription. 
     * 
     * This method checks for change in quantity, dates, and products. 
     * 
     * @param existingPool an existing pool referencing this subscription
     * @param sub the subscription
     */
    void updatePoolForSubscription(Pool existingPool, Subscription sub);

}
