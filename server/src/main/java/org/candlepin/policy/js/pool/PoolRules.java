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
package org.candlepin.policy.js.pool;

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.SourceSubscription;
import org.candlepin.service.model.SubscriptionInfo;

import com.google.inject.Inject;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;



/**
 * Rules for creation and updating of pools during a refresh pools operation.
 */
public class PoolRules {

    private static Logger log = LoggerFactory.getLogger(PoolRules.class);

    private PoolManager poolManager;
    private Configuration config;
    private EntitlementCurator entCurator;
    private OwnerProductCurator ownerProductCurator;
    private ProductCurator productCurator;

    @Inject
    public PoolRules(PoolManager poolManager, Configuration config, EntitlementCurator entCurator,
        OwnerProductCurator ownerProductCurator, ProductCurator productCurator) {

        this.poolManager = poolManager;
        this.config = config;
        this.entCurator = entCurator;
        this.ownerProductCurator = ownerProductCurator;
        this.productCurator = productCurator;
    }

    private long calculateQuantity(long quantity, Product product, String upstreamPoolId) {
        long result = quantity * (product.getMultiplier() != null ? product.getMultiplier() : 1);

        // In hosted, we increase the quantity on the subscription. However in standalone,
        // we assume this already has happened in hosted and the accurate quantity was
        // exported:
        String multiplier = product.getAttributeValue(Product.Attributes.INSTANCE_MULTIPLIER);

        if (multiplier != null && upstreamPoolId == null) {
            int instanceMultiplier = Integer.parseInt(multiplier);

            log.debug("Increasing pool quantity for instance multiplier: {}", instanceMultiplier);
            result = result * instanceMultiplier;
        }

        return result;
    }

    public List<Pool> createAndEnrichPools(SubscriptionInfo sub) {
        return createAndEnrichPools(sub, new LinkedList<>());
    }

    public List<Pool> createAndEnrichPools(SubscriptionInfo sub, List<Pool> existingPools) {
        Pool pool = this.poolManager.convertToMasterPool(sub);
        return createAndEnrichPools(pool, existingPools);
    }

    /**
     * Create any pools that need to be created for the given pool.
     *
     * In some scenarios, due to attribute changes, pools may need to be created even though
     * pools already exist for the subscription. A list of pre-existing pools for the given
     * sub are provided to help this method determine if something needs to be done or not.
     *
     * For a genuine new pool, the existing pools list will be empty.
     *
     * @param masterPool
     * @param existingPools
     * @return a list of pools created for the given pool
     */
    public List<Pool> createAndEnrichPools(Pool masterPool, List<Pool> existingPools) {
        List<Pool> pools = new LinkedList<>();

        long calculated = this.calculateQuantity(
            masterPool.getQuantity() != null ? masterPool.getQuantity() : 1, masterPool.getProduct(),
            masterPool.getUpstreamPoolId());

        masterPool.setQuantity(calculated);

        // The following will make virt_only a pool attribute. That makes the
        // pool explicitly virt_only to subscription manager and any other
        // downstream consumer.
        String virtOnly = masterPool.getProductAttributeValue(Product.Attributes.VIRT_ONLY);
        if (virtOnly != null && !virtOnly.isEmpty()) {
            masterPool.setAttribute(Pool.Attributes.VIRT_ONLY, virtOnly);
        }
        else {
            masterPool.removeAttribute(Pool.Attributes.VIRT_ONLY);
        }

        log.info("Checking if pools need to be created for: {}", masterPool);
        if (masterPool.getSubscriptionId() != null) {
            if (!hasMasterPool(existingPools)) {
                if (masterPool.getSubscriptionSubKey() != null &&
                    masterPool.getSubscriptionSubKey().contentEquals("derived")) {

                    // while we can create bonus pool from master pool, the reverse
                    // is not possible without the subscription itself
                    throw new IllegalStateException("Cannot create master pool from bonus pool");
                }

                pools.add(masterPool);
                log.info("Creating new master pool: {}", masterPool);
            }
        }
        else if (masterPool.getId() == null) {
            // This is a bit of a hack to ensure we still add net-new pools to the list of pools
            // to be created. When we get around to refactoring all of this for the pool hierarchy
            // stuff, this should be removed.
            pools.add(masterPool);
        }

        Pool bonusPool = createBonusPool(masterPool, existingPools);
        if (bonusPool != null) {
            pools.add(bonusPool);
        }

        return pools;
    }

    /*
     * If this subscription carries a virt_limit, we need to either create a
     * bonus pool for any guest (legacy behavior, only in hosted), or a pool for
     * temporary use of unmapped guests. (current behavior for any pool with
     * virt_limit)
     */
    private Pool createBonusPool(Pool masterPool, List<Pool> existingPools) {
        Map<String, String> attributes = masterPool.getProductAttributes();

        String virtQuantity = getVirtQuantity(
            attributes.get(Product.Attributes.VIRT_LIMIT), masterPool.getQuantity());

        log.info("Checking if bonus pools need to be created for pool: {}", masterPool);

        // Impl note:
        // We check if the pool has a source subscription to determine whether or not it's a custom
        // pool that doesn't originate from an subscription. If there isn't a source subscription,
        // we have no way of linking the bonus/derived pools to the master pool (at the time of
        // writing), and we'd end up with orphaned pools. If/when this issue is resolved, the check
        // for a source subscription should be removed.
        if (poolManager.isManaged(masterPool) && virtQuantity != null &&
            !hasBonusPool(existingPools)) {

            boolean hostLimited = "true".equals(attributes.get(Product.Attributes.HOST_LIMITED));
            HashMap<String, String> virtAttributes = new HashMap<>();
            virtAttributes.put(Pool.Attributes.VIRT_ONLY, "true");
            virtAttributes.put(Pool.Attributes.DERIVED_POOL, "true");
            virtAttributes.put(Pool.Attributes.PHYSICAL_ONLY, "false");
            if (hostLimited || config.getBoolean(ConfigProperties.STANDALONE)) {
                virtAttributes.put(Pool.Attributes.UNMAPPED_GUESTS_ONLY, "true");
            }

            // Make sure the virt pool does not have a virt_limit,
            // otherwise this will recurse infinitely
            virtAttributes.put(Product.Attributes.VIRT_LIMIT, "0");

            // Favor derived products if they are available
            Product sku = masterPool.getDerivedProduct() != null ? masterPool.getDerivedProduct() :
                masterPool.getProduct();

            // Using derived here because only one derived pool is created for
            // this subscription
            Pool bonusPool = PoolHelper.clonePool(masterPool, sku, virtQuantity, virtAttributes, "derived",
                ownerProductCurator, null, null, productCurator);

            log.info("Creating new derived pool: {}", bonusPool);
            return bonusPool;
        }

        return null;
    }

    private boolean hasMasterPool(List<Pool> pools) {
        if (pools != null) {
            for (Pool pool : pools) {
                SourceSubscription srcSub = pool.getSourceSubscription();
                if (srcSub != null && "master".equals(srcSub.getSubscriptionSubKey())) {
                    return true;
                }
            }
        }

        return false;
    }

    private boolean hasBonusPool(List<Pool> pools) {
        if (pools != null) {
            for (Pool pool : pools) {
                SourceSubscription srcSub = pool.getSourceSubscription();
                if (srcSub != null && "derived".equals(srcSub.getSubscriptionSubKey())) {
                    return true;
                }
            }
        }

        return false;
    }

    /*
     * Returns null if invalid
     */
    private String getVirtQuantity(String virtLimit, long quantity) {
        if ("unlimited".equals(virtLimit)) {
            return virtLimit;
        }

        try {
            int virtLimitInt = Integer.parseInt(virtLimit);
            if (virtLimitInt > 0) {
                return String.valueOf(virtLimitInt * quantity);
            }
        }
        catch (NumberFormatException nfe) {
            // Nothing to update if we get here.
            log.warn("Invalid virt_limit attribute specified.");
        }

        return null;
    }

    /**
     * Refresh pools which have no subscription tied (directly) to them.
     *
     * @param floatingPools pools with no subscription ID
     * @return pool updates
     */
    public List<PoolUpdate> updatePools(List<Pool> floatingPools, Map<String, Product> changedProducts) {
        List<PoolUpdate> updates = new LinkedList<>();
        for (Pool p : floatingPools) {

            if (p.getSubscriptionId() != null) {
                // Should be filtered out before calling, but just in case we skip it:
                continue;
            }

            if (p.isDevelopmentPool()) {
                continue;
            }

            if (p.getSourceStack() != null) {
                Consumer c = p.getSourceStack().getSourceConsumer();
                if (c == null) {
                    log.error("Stack derived pool has no source consumer: " + p.getId());
                }
                else {
                    PoolUpdate update = updatePoolFromStack(p, changedProducts);
                    if (update.changed()) {
                        updates.add(update);
                    }
                }
            }
        }

        return updates;
    }

    public List<PoolUpdate> updatePools(Pool masterPool, List<Pool> existingPools, Long originalQuantity,
        Map<String, Product> changedProducts) {
        //local.setCertificate(subscription.getCertificate());

        log.debug("Refreshing pools for existing master pool: {}", masterPool);
        log.debug("  existing pools: {}", existingPools.size());

        List<PoolUpdate> poolsUpdated = new LinkedList<>();
        Map<String, String> attributes = masterPool.getProductAttributes();

        for (Pool existingPool : existingPools) {
            log.debug("Checking pool: {}", existingPool);

            // Ensure subscription details are maintained on the master pool
            if ("master".equalsIgnoreCase(existingPool.getSubscriptionSubKey())) {
                existingPool.setUpstreamPoolId(masterPool.getUpstreamPoolId());
                existingPool.setUpstreamEntitlementId(masterPool.getUpstreamEntitlementId());
                existingPool.setUpstreamConsumerId(masterPool.getUpstreamConsumerId());

                existingPool.setCdn(masterPool.getCdn());
                existingPool.setCertificate(masterPool.getCertificate());
            }

            // Used to track if anything has changed:
            PoolUpdate update = new PoolUpdate(existingPool);

            update.setDatesChanged(checkForDateChange(masterPool.getStartDate(), masterPool.getEndDate(),
                existingPool));

            update.setQuantityChanged(checkForQuantityChange(masterPool, existingPool, originalQuantity,
                existingPools, attributes));

            if (!existingPool.isMarkedForDelete()) {
                boolean useDerived = masterPool.getDerivedProduct() != null &&
                    BooleanUtils.toBoolean(existingPool.getAttributeValue(Pool.Attributes.DERIVED_POOL));

                update.setProductsChanged(checkForChangedProducts(
                    useDerived ? masterPool.getDerivedProduct() : masterPool.getProduct(),
                    existingPool,
                    changedProducts)
                );

                if (!useDerived) {
                    update.setDerivedProductsChanged(
                        checkForChangedDerivedProducts(masterPool, existingPool, changedProducts));
                }

                update.setOrderChanged(checkForOrderDataChanges(masterPool, existingPool));
            }

            // All done, see if we found any changes and return an update object if so:
            if (update.changed()) {
                poolsUpdated.add(update);
            }
            else {
                log.debug("   No updates required.");
            }
        }

        return poolsUpdated;
    }

    /**
     * Updates the pool based on the entitlements in the specified stack.
     *
     * @param pool
     * @param changedProducts
     * @return pool update specifics
     */
    public PoolUpdate updatePoolFromStack(Pool pool, Map<String, Product> changedProducts) {
        List<Entitlement> stackedEnts = this.entCurator
            .findByStackId(pool.getSourceStack().getSourceConsumer(), pool.getSourceStackId())
            .list();

        return this.updatePoolFromStackedEntitlements(pool, stackedEnts, changedProducts);
    }

    /**
     * Updates the pool based on the entitlements in the specified stack.
     *
     * @param pools
     * @param consumer
     * @return updates
     */
    public List<PoolUpdate> updatePoolsFromStack(Consumer consumer, Collection<Pool> pools,
        Collection<Entitlement> entitlements, boolean deleteIfNoStackedEnts) {
        return updatePoolsFromStack(consumer, pools, entitlements, null, deleteIfNoStackedEnts);
    }

    /**
     * Updates the pool based on the entitlements in the specified stack.
     *
     * @param pools
     * @param consumer
     * @return updates
     */
    public List<PoolUpdate> updatePoolsFromStack(Consumer consumer, Collection<Pool> pools,
        Collection<Entitlement> newEntitlements, Collection<String> alreadyDeletedPools,
        boolean deleteIfNoStackedEnts) {

        Map<String, List<Entitlement>> entitlementMap = new HashMap<>();
        Set<String> sourceStackIds = new HashSet<>();
        List<PoolUpdate> result = new ArrayList<>();

        for (Pool pool : pools) {
            sourceStackIds.add(pool.getSourceStackId());
        }

        List<Entitlement> allEntitlements = this.entCurator.findByStackIds(consumer, sourceStackIds).list();
        if (CollectionUtils.isNotEmpty(newEntitlements)) {
            allEntitlements.addAll(newEntitlements);
        }

        for (Entitlement entitlement : allEntitlements) {
            List<Entitlement> ents = entitlementMap.get(entitlement.getPool().getStackId());
            if (ents == null) {
                ents = new ArrayList<>();
                entitlementMap.put(entitlement.getPool().getStackId(), ents);
            }

            ents.add(entitlement);
        }

        List<Pool> poolsToDelete = new ArrayList<>();
        for (Pool pool : pools) {
            List<Entitlement> entitlements = entitlementMap.get(pool.getSourceStackId());
            if (CollectionUtils.isNotEmpty(entitlements)) {
                result.add(this.updatePoolFromStackedEntitlements(pool, entitlements,
                    Collections.<String, Product>emptyMap()));
            }
            else if (deleteIfNoStackedEnts) {
                poolsToDelete.add(pool);
            }
        }

        if (!poolsToDelete.isEmpty()) {
            this.poolManager.deletePools(poolsToDelete, alreadyDeletedPools);
        }

        return result;
    }

    public PoolUpdate updatePoolFromStackedEntitlements(Pool pool, Collection<Entitlement> stackedEnts,
        Map<String, Product> changedProducts) {
        PoolUpdate update = new PoolUpdate(pool);

        // Nothing to do if there were no entitlements found.
        if (CollectionUtils.isEmpty(stackedEnts)) {
            return update;
        }

        pool.setSourceEntitlement(null);
        pool.setSourceSubscription(null);

        StackedSubPoolValueAccumulator acc = new StackedSubPoolValueAccumulator(stackedEnts);

        // Check if the quantity should be changed. If there was no
        // virt limiting entitlement, then we leave the quantity alone,
        // else, we set the quantity to that of the eldest virt limiting
        // entitlement pool.
        Entitlement eldestWithVirtLimit = acc.getEldestWithVirtLimit();
        if (eldestWithVirtLimit != null) {
            // Quantity may have changed, lets see.
            String virtLimit =
                eldestWithVirtLimit.getPool().getProductAttributeValue(Product.Attributes.VIRT_LIMIT);

            Long quantity = Pool.parseQuantity(virtLimit);

            if (!quantity.equals(pool.getQuantity())) {
                pool.setQuantity(quantity);
                update.setQuantityChanged(true);
            }
        }

        update.setDatesChanged(checkForDateChange(acc.getStartDate(), acc.getEndDate(), pool));

        // We use the "oldest" entitlement as the master for determining values that
        // could have come from the various subscriptions.
        Entitlement eldest = acc.getEldest();
        Pool eldestEntPool = eldest.getPool();
        boolean useDerived = eldestEntPool.getDerivedProduct() != null;
        Product product = useDerived ? eldestEntPool.getDerivedProduct() : eldestEntPool.getProduct();

        update.setProductAttributesChanged(
            !pool.getProductAttributes().equals(product.getAttributes())
        );

        // Check if product ID, name, or provided products have changed.
        update.setProductsChanged(checkForChangedProducts(product, pool, changedProducts));

        if (!StringUtils.equals(eldestEntPool.getContractNumber(), pool.getContractNumber()) ||
            !StringUtils.equals(eldestEntPool.getOrderNumber(), pool.getOrderNumber()) ||
            !StringUtils.equals(eldestEntPool.getAccountNumber(), pool.getAccountNumber())) {
            pool.setContractNumber(eldestEntPool.getContractNumber());
            pool.setAccountNumber(eldestEntPool.getAccountNumber());
            pool.setOrderNumber(eldestEntPool.getOrderNumber());
            update.setOrderChanged(true);
        }

        // If there are any changes made, then mark all the entitlements as dirty
        // so that they get regenerated on next checkin.
        if (update.changed()) {
            for (Entitlement ent : pool.getEntitlements()) {
                ent.setDirty(true);
            }
        }
        return update;
    }

    private boolean checkForOrderDataChanges(Pool pool, Pool existingPool) {
        boolean orderDataChanged = PoolHelper.checkForOrderChanges(existingPool, pool);
        if (orderDataChanged) {
            existingPool.setAccountNumber(pool.getAccountNumber());
            existingPool.setOrderNumber(pool.getOrderNumber());
            existingPool.setContractNumber(pool.getContractNumber());
        }
        return orderDataChanged;
    }

    private boolean checkForChangedProducts(Product incomingProduct, Pool existingPool,
        Map<String, Product> changedProducts) {

        Product existingProduct = existingPool.getProduct();
        String pid = existingProduct.getId();

        // TODO: ideally we would differentiate between these different product changes
        // a little, but in the end it probably doesn't matter:
        boolean productsChanged = pid != null && !pid.equals(incomingProduct.getId());

        // Check if the existing product is in the set of changed products
        if (!productsChanged && changedProducts != null) {
            if (pid != null) {
                productsChanged = (changedProducts.get(pid) != null);
            }
        }

        if (productsChanged) {
            existingPool.setProduct(incomingProduct);
        }

        return productsChanged;
    }

    private boolean checkForChangedDerivedProducts(Pool pool, Pool existingPool,
        Map<String, Product> changedProducts) {
        boolean dpChanged = false;
        Product existingDerived = existingPool.getDerivedProduct();
        Product incomingDerived = pool.getDerivedProduct();

        if (incomingDerived != null) {
            dpChanged = (existingDerived == null) ||
                (existingDerived.getId() != null &&
                !existingDerived.getId().equals(incomingDerived.getId())) ||
                (changedProducts != null && changedProducts.containsKey(existingDerived.getId()));
        }
        else {
            dpChanged = existingDerived != null;
        }

        if (dpChanged) {
            existingPool.setDerivedProduct(incomingDerived);
        }

        return dpChanged;
    }

    private boolean checkForDateChange(Date start, Date end, Pool existingPool) {
        boolean datesChanged = (!start.equals(existingPool.getStartDate())) ||
            (!end.equals(existingPool.getEndDate()));

        if (datesChanged) {
            existingPool.setStartDate(start);
            existingPool.setEndDate(end);
        }

        return datesChanged;
    }

    private boolean checkForQuantityChange(Pool pool, Pool existingPool, Long originalQuantity,
        List<Pool> existingPools, Map<String, String> attributes) {

        // Expected quantity is normally the main pool's quantity, but for
        // virt only pools we expect it to be main pool quantity * virt_limit:
        long expectedQuantity = calculateQuantity(originalQuantity, pool.getProduct(),
            pool.getUpstreamPoolId());

        expectedQuantity = processVirtLimitPools(existingPools,
            attributes, existingPool, expectedQuantity);

        boolean quantityChanged = !(expectedQuantity == existingPool.getQuantity());

        if (quantityChanged) {
            existingPool.setQuantity(expectedQuantity);
        }

        return quantityChanged;
    }

    private long processVirtLimitPools(List<Pool> existingPools,
        Map<String, String> attributes, Pool existingPool, long expectedQuantity) {
        /*
         *  WARNING: when updating pools, we have the added complication of having to
         *  watch out for pools that candlepin creates internally. (i.e. virt bonus
         *  pools in hosted (created when sub is first detected), and host restricted
         *  virt pools when on-site. (created when a host binds)
         */

        /* Check the product attribute off the subscription too because
         * derived products on the subscription are graduated to be the pool products and
         * derived products aren't going to have a virt_limit attribute
         */
        if (existingPool.hasAttribute(Pool.Attributes.DERIVED_POOL) &&
            "true".equalsIgnoreCase(existingPool.getAttributeValue(Pool.Attributes.VIRT_ONLY)) &&
            (attributes.containsKey(Product.Attributes.VIRT_LIMIT) ||
            existingPool.getProduct().hasAttribute(Product.Attributes.VIRT_LIMIT))) {

            if (!attributes.containsKey(Product.Attributes.VIRT_LIMIT)) {
                log.warn("virt_limit attribute has been removed from subscription, " +
                    "flagging pool for deletion if supported: {}", existingPool.getId());
                // virt_limit has been removed! We need to clean up this pool. Set
                // attribute to notify the server of this:
                existingPool.setMarkedForDelete(true);
                // Older candlepin's won't look at the delete indicator, so we will
                // set the expected quantity to 0 to effectively disable the pool
                // on those servers as well.
                expectedQuantity = 0;
            }
            else {
                String virtLimitStr = attributes.get(Product.Attributes.VIRT_LIMIT);

                if ("unlimited".equals(virtLimitStr)) {
                    // 0 will only happen if the rules set it to be 0 -- don't modify
                    // -1 for pretty much all the rest
                    expectedQuantity = existingPool.getQuantity() == 0 ?
                        0 : -1;
                }
                else {
                    try {
                        int virtLimit = Integer.parseInt(virtLimitStr);
                        if (config.getBoolean(ConfigProperties.STANDALONE) && !"true".equals(
                            existingPool.getAttributeValue(Pool.Attributes.UNMAPPED_GUESTS_ONLY))) {

                            // this is how we determined the quantity
                            expectedQuantity = virtLimit;
                        }
                        else {
                            // we need to see if a parent pool exists and has been
                            // exported. Adjust is number exported from a parent pool.
                            // If no parent pool, adjust = 0 [a scenario of virtual pool
                            // only]
                            //
                            // WARNING: we're assuming there is only one base
                            // (non-derived) pool. This may change in the future
                            // requiring a more complex
                            // adjustment for exported quantities if there are multiple
                            // pools in play.
                            long adjust = 0L;
                            for (Pool derivedPool : existingPools) {
                                String isDerived =
                                    derivedPool.getAttributeValue(Pool.Attributes.DERIVED_POOL);

                                if (isDerived == null) {
                                    adjust = derivedPool.getExported();
                                }
                            }
                            expectedQuantity = (expectedQuantity - adjust) * virtLimit;
                        }
                    }
                    catch (NumberFormatException nfe) {
                        // Nothing to update if we get here.
//                            continue;
                    }
                }
            }
        }
        return expectedQuantity;
    }

}
