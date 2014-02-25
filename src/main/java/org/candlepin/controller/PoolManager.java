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

import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Environment;
import org.candlepin.model.FilterBuilder;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Subscription;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.js.pool.PoolUpdate;

import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 *
 */
public interface PoolManager {

    // WARNING: Changing these will impact rules files in the wild.
    String DELETE_FLAG = "candlepin.delete_pool";

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

    Entitlement ueberCertEntitlement(Consumer consumer, Pool pool,
        Integer quantity) throws EntitlementRefusedException;

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
     * @param entitleDate specific date to entitle by.
     * @return Entitlement
     * @throws EntitlementRefusedException if entitlement is refused
     */
    List<Entitlement> entitleByProducts(Consumer consumer, String[] productIds,
        Date entitleDate)
        throws EntitlementRefusedException;

    List<PoolQuantity> getBestPools(Consumer consumer, String[] productIds,
        Date entitleDate, Owner owner, String serviceLevelOverride)
        throws EntitlementRefusedException;

    Pool find(String poolId);

    List<Pool> lookupBySubscriptionId(String id);

    Refresher getRefresher();
    Refresher getRefresher(boolean lazy);

    /**
     * @param e
     * @param ueberCertificate TODO
     */
    void regenerateCertificatesOf(Entitlement e, boolean ueberCertificate, boolean lazy);

    void regenerateCertificatesOf(Environment env, Set<String> contentIds, boolean lazy);

    void regenerateCertificatesOf(String productId, boolean lazy);

    void regenerateEntitlementCertificates(Consumer consumer, boolean lazy);

    int revokeAllEntitlements(Consumer consumer);

    int removeAllEntitlements(Consumer consumer);

    void revokeEntitlement(Entitlement entitlement);

    Pool updatePoolQuantity(Pool pool, long adjust);

    Pool setPoolQuantity(Pool pool, long set);

    void regenerateDirtyEntitlements(List<Entitlement> entitlements);

    Entitlement adjustEntitlementQuantity(Consumer consumer, Entitlement entitlement,
        Integer quantity) throws EntitlementRefusedException;

    /**
     * Search for any expired pools on the server, cleanup their subscription,
     * entitlements, and the pool itself.
     */
    void cleanupExpiredPools();


    /**
     * List entitlement pools.
     *
     * If a consumer is specified, a pass through the rules will be done for
     * each potentially usable pool.
     *
     * @param consumer Consumer being entitled.
     * @param owner Owner whose subscriptions should be inspected.
     * @param productId only entitlements which provide this product are included.
     * @param activeOn Indicates to return only pools valid on this date.
     *        Set to null for no date filtering.
     * @param activeOnly if true, only active entitlements are included.
     * @param includeWarnings When filtering by consumer, include pools that
     *        triggered a rule warning. (errors will still be excluded)
     * @param filterBuilder builds and applies all filters when looking up pools.
     * @param pageRequest used to determine if results paging is required.
     * @return List of entitlement pools.
     */
    Page<List<Pool>> listAvailableEntitlementPools(Consumer consumer, Owner owner,
        String productId, Date activeOn, boolean activeOnly, boolean includeWarnings,
        FilterBuilder filterBuilder, PageRequest pageRequest);

    /**
     *  Get the available service levels for consumers for this owner. Exempt
     *  means that a product pool with this level can be used with a consumer of any
     *  service level.
     *
     * @param owner The owner that has the list of available service levels for
     *              its consumers
     * @param exempt boolean to show if the desired list is the levels that are
     *               explicitly marked with the support_level_exempt attribute.
     * @return Set of levels based on exempt flag.
     */
    Set<String> retrieveServiceLevelsForOwner(Owner owner, boolean exempt);

    /**
     * Finds the entitlements for the specified Pool.
     *
     * @param pool look for entitlements from this Pool.
     * @return a list of entitlements
     */
    List<Entitlement> findEntitlements(Pool pool);

    /**
     * Find the Ueber pool for this owner
     *
     * @param owner the owner to fetch the pool from
     * @return the Ueber pool
     */
    Pool findUeberPool(Owner owner);

    /**
     * Lists the pools for the specified Owner.
     *
     * @param owner the Owner to get the pools for
     * @return a list of pools for the specified Owner
     */
    List<Pool> listPoolsByOwner(Owner owner);

    /**
     * Updates the pool based on the entitlements in the specified stack.
     * @param pool
     * @param consumer
     * @param stackId
     *
     * @return pool update specifics
     */
    PoolUpdate updatePoolFromStack(Pool pool, Consumer consumer, String stackId);

    /**
     * @param guest products we want to provide for
     * @param host to bind entitlements to
     * @param entitleDate
     * @param owner
     * @param serviceLevelOverride
     * @return list of entitlements to bind
     * @throws EntitlementRefusedException if unable to bind
     */
    List<PoolQuantity> getBestPoolsForHost(Consumer guest,
        Consumer host, Date entitleDate, Owner owner,
        String serviceLevelOverride) throws EntitlementRefusedException;

    /**
     * @param consumer
     * @param host
     * @param entitleDate
     * @return list of entitlements to bind
     * @throws EntitlementRefusedException if unable to bind
     */
    List<Entitlement> entitleByProductsForHost(Consumer consumer,
        Consumer host, Date entitleDate)
        throws EntitlementRefusedException;
}
