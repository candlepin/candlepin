/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.candlepin.model.CandlepinQuery;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolFilterBuilder;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.paging.Page;
import org.candlepin.paging.PageRequest;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.js.pool.PoolUpdate;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.service.model.SubscriptionInfo;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 *
 */
public interface PoolManager {

    Pool createPool(Pool p);

    List<Pool> createPools(List<Pool> pools);

    /**
     * @param sub
     * @return the newly created Pool(s)
     */
    List<Pool> createAndEnrichPools(SubscriptionInfo sub);

    /**
     * Create any pools that need to be created for the given pool.
     *
     * @param pool
     * @return original pool, enriched
     */
    Pool createAndEnrichPools(Pool pool);

    Pool createAndEnrichPools(Pool pool, List<Pool> existingPools);

    Pool convertToPrimaryPool(SubscriptionInfo subscription);

    /**
     * Applies changes to the given pool to itself and any of its derived pools. This may result in
     * a deletion of the pool if it has been expired as a result of the changes.
     *
     * @param pool
     *  The pool to update
     */
    void updatePrimaryPool(Pool pool);

    /**
     * Deletes the pools associated with the specified subscription IDs.
     *
     * @param subscriptionIds
     *  A collection of subscription IDs used to lookup and delete pools
     */
    void deletePoolsForSubscriptions(Collection<String> subscriptionIds);

    /**
     * Cleanup entitlements and safely delete the given pool.
     *
     * @param pool
     */
    void deletePool(Pool pool);

    /**
     * Request an entitlement by poolid and quantity
     *
     * @param consumer consumer requesting to be entitled
     * @param poolQuantities a map of entitlement pool ids and the respective
     *        quantities to consume from
     * @return Entitlements A list of entitlements created if the request is
     *         successful
     * @throws EntitlementRefusedException if entitlement is refused
     */
    List<Entitlement> entitleByPools(Consumer consumer, Map<String, Integer> poolQuantities)
        throws EntitlementRefusedException;

    /**
     * Request an entitlement by product.
     *
     * If the entitlement cannot be granted, null will be returned.
     *
     * TODO: Throw exception if entitlement not granted. Report why.
     *
     * @param data AutobindData encapsulating data required for an autobind request
     * @return Entitlement
     * @throws EntitlementRefusedException if entitlement is refused
     */
    List<Entitlement> entitleByProducts(AutobindData data)
        throws EntitlementRefusedException;

    List<PoolQuantity> getBestPools(Consumer consumer, Collection<String> productIds,
        Date entitleDate, String ownerId, String serviceLevelOverride, Collection<String> fromPools)
        throws EntitlementRefusedException;

    Pool get(String poolId);

    List<Pool> secureGet(Collection<String> poolId);

    List<String> listEntitledConsumerUuids(String poolId);

    List<Pool> getBySubscriptionId(Owner owner, String id);

    List<Pool> getBySubscriptionIds(String ownerId, Collection<String> id);

    int revokeAllEntitlements(Consumer consumer);
    int revokeAllEntitlements(Consumer consumer, boolean regenCertsAndStatuses);

    Set<Pool> revokeEntitlements(List<Entitlement> ents);
    void revokeEntitlement(Entitlement entitlement);

    Pool setPoolQuantity(Pool pool, long set);

    void setPoolQuantity(Map<Pool, Long> poolQuantities);

    void regenerateDirtyEntitlements(Consumer consumer);

    void regenerateDirtyEntitlements(Iterable<Entitlement> entitlements);

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
     * @param ownerId Owner whose subscriptions should be inspected.
     * @param productId only entitlements which provide this product are included.
     * @param activeOn Indicates to return only pools valid on this date.
     *        Set to null for no date filtering.
     * @param includeWarnings When filtering by consumer, include pools that
     *        triggered a rule warning. (errors will still be excluded)
     * @param filterBuilder builds and applies all filters when looking up pools.
     * @param pageRequest used to determine if results paging is required.
     * @return List of entitlement pools.
     */
    Page<List<Pool>> listAvailableEntitlementPools(Consumer consumer, ActivationKey key,
        String ownerId, String productId, String subscriptionId, Date activeOn,
        boolean includeWarnings, PoolFilterBuilder filterBuilder, PageRequest pageRequest,
        boolean addFuture, boolean onlyFuture, Date after);

    /**
     *  Get the available service levels for consumers for this owner. Exempt
     *  means that a product pool with this level can be used with a consumer of any
     *  service level.
     *
     * @param ownerId The ownerId that has the list of available service levels for
     *              its consumers
     * @param exempt boolean to show if the desired list is the levels that are
     *               explicitly marked with the support_level_exempt attribute.
     * @return Set of levels based on exempt flag.
     */
    Set<String> retrieveServiceLevelsForOwner(String ownerId, boolean exempt);

    /**
     * Lists the pools for the specified Owner.
     *
     * @param owner the Owner to get the pools for
     * @return a list of pools for the specified Owner
     */
    CandlepinQuery<Pool> listPoolsByOwner(Owner owner);

    /**
     * Updates the pool based on the entitlements in the specified stack.
     *
     * @param pool
     * @param changedProducts
     * @param force
     *  whether or not to apply the update even in cases where an explicit data change is not
     *  detected
     *
     * @return pool update specifics
     */
    PoolUpdate updatePoolFromStack(Pool pool, Map<String, Product> changedProducts, boolean force);

    /**
     * Updates the pools based on the entitlements in the specified stack.
     *
     * @param consumer
     * @param pool
     */
    void updatePoolsFromStackWithoutDeletingStack(Consumer consumer,
        List<Pool> pool, Collection<Entitlement> entitlements);

    /**
     * @param guest products we want to provide for
     * @param host to bind entitlements to
     * @param entitleDate
     * @param ownerId
     * @param serviceLevelOverride
     * @param fromPools
     * @return list of entitlements to bind
     * @throws EntitlementRefusedException if unable to bind
     */
    List<PoolQuantity> getBestPoolsForHost(Consumer guest,
        Consumer host, Date entitleDate, String ownerId,
        String serviceLevelOverride, Collection<String> fromPools) throws EntitlementRefusedException;

    /**
     * @param guest
     * @param host
     * @param entitleDate
     * @param possiblePools
     * @return list of entitlements to bind
     * @throws EntitlementRefusedException if unable to bind
     */
    List<Entitlement> entitleByProductsForHost(Consumer guest, Consumer host,
        Date entitleDate, Collection<String> possiblePools)
        throws EntitlementRefusedException;

    /**
     * Retrieves a list of pools associated with the specified subscription ID. If there are no
     * pools associated with the given subscription, this method should return an empty list.
     *
     * @param subscriptionId
     *  The subscription ID to use to lookup pools
     *
     * @return
     *  a list of pools associated with the specified subscription.
     */
    CandlepinQuery<Pool> getPoolsBySubscriptionId(String subscriptionId);

    /**
     * Retrieves a list consisting of all known primary pools.
     *
     * @return
     *  a list of known primary pools
     */
    CandlepinQuery<Pool> getPrimaryPools();

    void deletePools(Collection<Pool> pools);

    void deletePools(Collection<Pool> pools, Collection<String> alreadyDeletedPools);

    void checkBonusPoolQuantities(String ownerId,
        Map<String, Entitlement> entitlements);

}
