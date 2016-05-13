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

import org.candlepin.common.paging.Page;
import org.candlepin.common.paging.PageRequest;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Environment;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolFilterBuilder;
import org.candlepin.model.PoolQuantity;
import org.candlepin.model.Product;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.dto.Subscription;
import org.candlepin.policy.EntitlementRefusedException;
import org.candlepin.policy.js.pool.PoolUpdate;
import org.candlepin.resource.dto.AutobindData;
import org.candlepin.service.SubscriptionServiceAdapter;

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
    List<Pool> createAndEnrichPools(Subscription sub);

    /**
     * Create any pools that need to be created for the given pool.
     *
     * @param pool
     * @return original pool, enriched
     */
    Pool createAndEnrichPools(Pool pool);

    Pool createAndEnrichPools(Pool pool, List<Pool> existingPools);

    Pool convertToMasterPool(Subscription subscription);

    /**
     * Updates the pools using the information stored in the given pool. Because
     * the input subscription is used to lookup pools, the ID field must be set
     * for this method to operate properly.
     *
     * @param pool
     *        The pool to use for updating the associated pools
     */
    void updateMasterPool(Pool pool);

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

    Entitlement ueberCertEntitlement(Consumer consumer, Pool pool) throws EntitlementRefusedException;

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

    List<PoolQuantity> getBestPools(Consumer consumer, String[] productIds,
        Date entitleDate, Owner owner, String serviceLevelOverride, Collection<String> fromPools)
        throws EntitlementRefusedException;

    Pool find(String poolId);

    List<Pool> secureFind(Collection<String> poolId);

    List<Pool> lookupBySubscriptionId(String id);

    List<Pool> lookupBySubscriptionIds(Collection<String> id);

    Refresher getRefresher(SubscriptionServiceAdapter subAdapter);
    Refresher getRefresher(SubscriptionServiceAdapter subAdapter, boolean lazy);

    /**
     * @param e
     * @param ueberCertificate TODO
     */
    void regenerateCertificatesOf(Entitlement e, boolean ueberCertificate, boolean lazy);

    void regenerateCertificatesOf(Environment env, Set<String> contentIds, boolean lazy);

    void regenerateCertificatesOf(Owner owner, String productId, boolean lazy);

    void regenerateCertificatesOf(Consumer consumer, boolean lazy);

    int revokeAllEntitlements(Consumer consumer);
    int revokeAllEntitlements(Consumer consumer, boolean regenCertsAndStatuses);

    void revokeEntitlements(List<Entitlement> ents);
    void revokeEntitlement(Entitlement entitlement);

    Pool updatePoolQuantity(Pool pool, long adjust);

    Pool setPoolQuantity(Pool pool, long set);

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
    Page<List<Pool>> listAvailableEntitlementPools(Consumer consumer, ActivationKey key,
        Owner owner, String productId, String subscriptionId, Date activeOn, boolean activeOnly,
        boolean includeWarnings, PoolFilterBuilder filterBuilder, PageRequest pageRequest);

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
     *
     * @return pool update specifics
     */
    PoolUpdate updatePoolFromStack(Pool pool, Set<Product> changedProducts);

    /**
     * Updates the pools based on the entitlements in the specified stack.
     *
     * @param consumer
     * @param pools
     */
    void updatePoolsFromStack(Consumer consumer, List<Pool> pool);

    /**
     * @param guest products we want to provide for
     * @param host to bind entitlements to
     * @param entitleDate
     * @param owner
     * @param serviceLevelOverride
     * @param fromPools
     * @return list of entitlements to bind
     * @throws EntitlementRefusedException if unable to bind
     */
    List<PoolQuantity> getBestPoolsForHost(Consumer guest,
        Consumer host, Date entitleDate, Owner owner,
        String serviceLevelOverride, Collection<String> fromPools) throws EntitlementRefusedException;

    /**
     * @param consumer
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
     * Creates a Subscription object using information derived from the specified pool. Used to
     * support deprecated API calls that still require a subscription.
     *
     * @param pool
     *  The pool from which to build a subscription
     *
     * @return
     *  a new subscription object derived from the specified pool.
     */
    Subscription fabricateSubscriptionFromPool(Pool pool);

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
    List<Pool> getPoolsBySubscriptionId(String subscriptionId);

    /**
     * Retrieves the master pool associated with the specified subscription ID. If there is not a
     * master pool asscoated with the given subscription, this method should return null.
     *
     * @param subscriptionId
     *  The subscription ID to use to lookup a master pool
     *
     * @return
     *  the master pool associated with the specified subscription.
     */
    Pool getMasterPoolBySubscriptionId(String subscriptionId);

    /**
     * Retrieves a list consisting of all known master pools.
     *
     * @return
     *  a list of known master pools
     */
    List<Pool> listMasterPools();

    void deletePools(List<Pool> pools);
}
