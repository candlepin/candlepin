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
import org.candlepin.model.Branding;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.PoolAttribute;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.ProductCurator;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.dto.Subscription;

import com.google.inject.Inject;

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private ProductCurator prodCurator;

    @Inject
    public PoolRules(PoolManager poolManager, Configuration config, EntitlementCurator entCurator,
        ProductCurator prodCurator) {

        this.poolManager = poolManager;
        this.config = config;
        this.entCurator = entCurator;
        this.prodCurator = prodCurator;
    }

    private long calculateQuantity(long quantity, Product product, String upstreamPoolId) {
        long result = quantity * product.getMultiplier();

        // In hosted, we increase the quantity on the subscription. However in standalone,
        // we assume this already has happened in hosted and the accurate quantity was
        // exported:
        if (product.hasAttribute("instance_multiplier") &&
                upstreamPoolId == null) {

            int instanceMultiplier = Integer.parseInt(
                    product.getAttributeValue("instance_multiplier"));
            log.debug("Increasing pool quantity for instance multiplier: " +
                instanceMultiplier);
            result = result * instanceMultiplier;
        }
        return result;
    }

    public List<Pool> createAndEnrichPools(Subscription sub) {
        return createAndEnrichPools(sub, new LinkedList<Pool>());
    }

    public List<Pool> createAndEnrichPools(Subscription sub, List<Pool> existingPools) {
        Pool pool = convertToPool(sub);
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
     * @param pool
     * @param existingPools
     * @return a list of pools created for the given pool
     */
    public List<Pool> createAndEnrichPools(Pool pool, List<Pool> existingPools) {
        List<Pool> pools = new LinkedList<Pool>();
        pool.setQuantity(calculateQuantity(pool.getQuantity(), pool.getProduct(), pool.getUpstreamPoolId()));

        ProductAttribute virtAtt = pool.getProduct().getAttribute("virt_only");
        // The following will make virt_only a pool attribute. That makes the
        // pool explicitly virt_only to subscription manager and any other
        // downstream consumer.
        if (virtAtt != null && virtAtt.getValue() != null && !virtAtt.getValue().equals("")) {
            pool.addAttribute(new PoolAttribute("virt_only", virtAtt.getValue()));
        }

        log.info("Checking if pools need to be created for: {}", pool);
        if (!hasMasterPool(existingPools)) {
            if (pool.getSourceSubscription() != null &&
                    pool.getSourceSubscription().getSubscriptionSubKey().contentEquals("derived")) {
                // while we can create bonus pool from master pool, the reverse
                // is not possible without the subscription itself
                throw new IllegalStateException("Cannot create master pool from bonus pool");
            }
            pools.add(pool);
            log.info("Creating new master pool: {}", pool);
        }
        Pool bonusPool = createBonusPool(pool, existingPools);
        if (bonusPool != null) {
            pools.add(bonusPool);
        }
        return pools;
    }

    /*
     * if you are using this method, you might want to override the quantity
     * with calculateQuantity
     */
    public Pool convertToPool(Subscription sub) {
        if (sub == null) {
            throw new IllegalArgumentException("subscription is null");
        }
        Pool pool = new Pool(sub.getOwner(), sub.getProduct(), sub.getProvidedProducts(),
                sub.getQuantity(), sub.getStartDate(), sub.getEndDate(), sub.getContractNumber(),
                sub.getAccountNumber(), sub.getOrderNumber());

        // Add all product references
        pool.setDerivedProduct(sub.getDerivedProduct());
        pool.setDerivedProvidedProducts(sub.getDerivedProvidedProducts());

        // Add in branding
        for (Branding b : sub.getBranding()) {
            pool.getBranding().add(new Branding(b.getProductId(), b.getType(), b.getName()));
        }
        pool.setSubscriptionId(sub.getId());
        pool.setSourceSubscription(new SourceSubscription(sub.getId(), "master"));

        // Copy over upstream details...?
        pool.setUpstreamPoolId(sub.getUpstreamPoolId());
        pool.setUpstreamEntitlementId(sub.getUpstreamEntitlementId());
        pool.setUpstreamConsumerId(sub.getUpstreamConsumerId());
        pool.setCdn(sub.getCdn());
        pool.setCertificate(sub.getCertificate());
        return pool;
    }

    /*
     * If this subscription carries a virt_limit, we need to either create a
     * bonus pool for any guest (legacy behavior, only in hosted), or a pool for
     * temporary use of unmapped guests. (current behavior for any pool with
     * virt_limit)
     */
    private Pool createBonusPool(Pool masterPool, List<Pool> existingPools) {
        PoolHelper helper = new PoolHelper(this.poolManager, null);
        Map<String, String> attributes = helper.getFlattenedAttributes(masterPool.getProduct());
        String virtQuantity = getVirtQuantity(attributes.get("virt_limit"), masterPool.getQuantity());

        log.info("Checking if bonus pools need to be created for pool: {}", masterPool);

        if (attributes.containsKey("virt_limit") && !hasBonusPool(existingPools) && virtQuantity != null) {

            boolean hostLimited = attributes.containsKey("host_limited") &&
                    attributes.get("host_limited").equals("true");
            HashMap<String, String> virtAttributes = new HashMap<String, String>();
            virtAttributes.put("virt_only", "true");
            virtAttributes.put("pool_derived", "true");
            virtAttributes.put("physical_only", "false");
            if (hostLimited || config.getBoolean(ConfigProperties.STANDALONE)) {
                virtAttributes.put("unmapped_guests_only", "true");
            }
            // Make sure the virt pool does not have a virt_limit,
            // otherwise this will recurse infinitely
            virtAttributes.put("virt_limit", "0");

            // Favor derived products if they are available
            Product sku = masterPool.getDerivedProduct() != null ? masterPool.getDerivedProduct() :
                    masterPool.getProduct();

            // Using derived here because only one derived pool is created for
            // this subscription
            Pool bonusPool = helper.clonePool(masterPool, sku, virtQuantity, virtAttributes, "derived",
                    prodCurator);

            log.info("Creating new derived pool: {}", bonusPool);
            return bonusPool;
        }
        return null;
    }

    private boolean hasMasterPool(List<Pool> pools) {
        if (pools != null) {
            for (Pool p : pools) {
                if (p.getSourceSubscription().getSubscriptionSubKey().equals("master")) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean hasBonusPool(List<Pool> pools) {
        if (pools != null) {
            for (Pool p : pools) {
                if (p.getSourceSubscription().getSubscriptionSubKey().equals("derived")) {
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
    public List<PoolUpdate> updatePools(List<Pool> floatingPools,
            Set<Product> changedProducts) {
        List<PoolUpdate> updates = new LinkedList<PoolUpdate>();
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

    public List<PoolUpdate> updatePools(Subscription sub, List<Pool> existingPools,
            Set<Product> changedProducts) {

        //local.setCertificate(subscription.getCertificate());

        log.debug("Refreshing pools for existing subscription: " + sub);
        log.debug("  existing pools: " + existingPools.size());
        PoolHelper helper = new PoolHelper(this.poolManager, null);

        List<PoolUpdate> poolsUpdated = new LinkedList<PoolUpdate>();
        Map<String, String> attributes = helper.getFlattenedAttributes(sub.getProduct());
        for (Pool existingPool : existingPools) {
            log.debug("Checking pool: " + existingPool.getId());

            // Ensure subscription details are maintained on the master pool
            if ("master".equalsIgnoreCase(existingPool.getSubscriptionSubKey())) {
                existingPool.setUpstreamPoolId(sub.getUpstreamPoolId());
                existingPool.setUpstreamEntitlementId(sub.getUpstreamEntitlementId());
                existingPool.setUpstreamConsumerId(sub.getUpstreamConsumerId());

                existingPool.setCdn(sub.getCdn());
                existingPool.setCertificate(sub.getCertificate());
            }

            // Used to track if anything has changed:
            PoolUpdate update = new PoolUpdate(existingPool);

            update.setDatesChanged(checkForDateChange(sub.getStartDate(),
                sub.getEndDate(), existingPool));

            update.setQuantityChanged(
                checkForQuantityChange(sub, existingPool, existingPools, attributes));

            if (!existingPool.isMarkedForDelete()) {
                boolean useDerived = BooleanUtils.toBoolean(existingPool.getAttributeValue("pool_derived")) &&
                    sub.getDerivedProduct() != null;

                update.setProductsChanged(
                    checkForChangedProducts(
                        sub.getProduct(),
                        getExpectedProvidedProducts(sub, existingPool, useDerived),
                        existingPool,
                        changedProducts
                    )
                );

                if (!useDerived) {
                    update.setDerivedProductsChanged(
                        checkForChangedDerivedProducts(sub, existingPool, changedProducts));
                }

                update.setOrderChanged(checkForOrderDataChanges(sub, helper,
                    existingPool));

                update.setBrandingChanged(checkForBrandingChanges(sub, existingPool));
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
     * @param pool
     * @param consumer
     * @param stackId
     *
     * @return pool update specifics
     */
    public PoolUpdate updatePoolFromStack(Pool pool, Set<Product> changedProducts) {
        List<Entitlement> stackedEnts = this.entCurator.findByStackId(
            pool.getSourceConsumer(), pool.getSourceStackId()
        );

        return this.updatePoolFromStackedEntitlements(pool, stackedEnts, changedProducts);
    }

    public PoolUpdate updatePoolFromStackedEntitlements(Pool pool, List<Entitlement> stackedEnts,
        Set<Product> changedProducts) {

        PoolUpdate update = new PoolUpdate(pool);

        // Nothing to do if there were no entitlements found.
        if (stackedEnts.isEmpty()) {
            return update;
        }

        pool.setSourceEntitlement(null);
        pool.setSourceSubscription(null);

        StackedSubPoolValueAccumulator acc = new StackedSubPoolValueAccumulator(pool, stackedEnts);

        // Check if the quantity should be changed. If there was no
        // virt limiting entitlement, then we leave the quantity alone,
        // else, we set the quantity to that of the eldest virt limiting
        // entitlement pool.
        Entitlement eldestWithVirtLimit = acc.getEldestWithVirtLimit();
        if (eldestWithVirtLimit != null) {
            // Quantity may have changed, lets see.
            String virtLimit =
                eldestWithVirtLimit.getPool().getProductAttributeValue("virt_limit");

            Long quantity =
                virtLimit.equalsIgnoreCase("unlimited") ? -1L : Long.parseLong(virtLimit);

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
        update.setProductsChanged(checkForChangedProducts(
            product, acc.getExpectedProvidedProds(), pool, changedProducts
        ));

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

    private boolean checkForOrderDataChanges(Subscription sub,
        PoolHelper helper, Pool existingPool) {
        boolean orderDataChanged = helper.checkForOrderChanges(existingPool, sub);
        if (orderDataChanged) {
            existingPool.setAccountNumber(sub.getAccountNumber());
            existingPool.setOrderNumber(sub.getOrderNumber());
            existingPool.setContractNumber(sub.getContractNumber());
        }
        return orderDataChanged;
    }

    private boolean checkForBrandingChanges(Subscription sub, Pool existingPool) {
        boolean brandingChanged = false;

        if (sub.getBranding().size() != existingPool.getBranding().size()) {
            brandingChanged = true;
        }
        else {
            for (Branding b : sub.getBranding()) {
                if (!existingPool.getBranding().contains(b)) {
                    syncBranding(sub, existingPool);
                    brandingChanged = true;
                    break;
                }
            }
        }

        if (brandingChanged) {
            syncBranding(sub, existingPool);
        }
        return brandingChanged;
    }

    /*
     * Something has changed, sync the branding.
     */
    private void syncBranding(Subscription sub, Pool pool) {
        pool.getBranding().clear();
        for (Branding b : sub.getBranding()) {
            pool.getBranding().add(new Branding(b.getProductId(), b.getType(),
                b.getName()));
        }
    }

    private Set<Product> getExpectedProvidedProducts(Subscription sub, Pool existingPool,
        boolean useDerived) {

        Set<Product> incomingProvided = new HashSet<Product>();
        Set<Product> source = useDerived ?
            sub.getDerivedProvidedProducts() :
            sub.getProvidedProducts();

        if (source != null && !source.isEmpty()) {
            incomingProvided.addAll(source);
        }

        return incomingProvided;
    }

    private boolean checkForChangedProducts(Product incomingProduct, Set<Product> incomingProvided,
        Pool existingPool, Set<Product> changedProducts) {

        Product existingProduct = existingPool.getProduct();
        Set<Product> currentProvided = existingPool.getProvidedProducts();

        // TODO: ideally we would differentiate between these different product changes
        // a little, but in the end it probably doesn't matter:
        boolean productsChanged =
            !incomingProduct.getId().equals(existingProduct.getId()) ||
            (changedProducts != null && changedProducts.contains(existingProduct)) ||
            !currentProvided.equals(incomingProvided);

        if (productsChanged) {
            existingPool.setProduct(incomingProduct);
            existingPool.setProvidedProducts(incomingProvided);
        }

        return productsChanged;
    }

    private boolean checkForChangedDerivedProducts(Subscription sub, Pool existingPool,
            Set<Product> changedProducts) {

        boolean productsChanged = false;
        if (sub.getDerivedProduct() != null) {
            productsChanged = !sub.getDerivedProduct().getId().equals(
                existingPool.getDerivedProduct().getId());
            productsChanged = productsChanged ||
                    (changedProducts != null &&
                    changedProducts.contains(sub.getDerivedProduct()));
        }

        // Build expected set of ProvidedProducts and compare:
        Set<Product> currentProvided = existingPool.getDerivedProvidedProducts();
        Set<Product> incomingProvided = new HashSet<Product>();
        if (sub.getDerivedProvidedProducts() != null) {
            for (Product p : sub.getDerivedProvidedProducts()) {
                incomingProvided.add(p);
            }
        }
        productsChanged = productsChanged || !currentProvided.equals(incomingProvided);

        if (productsChanged) {
            // 998317: NPE during refresh causes refresh to abort.
            // Above we check getDerivedProduct for null, but here
            // we ignore the fact that it may be null. So we will
            // now check for null to avoid blowing up.
            if (sub.getDerivedProduct() != null) {
                existingPool.setDerivedProduct(sub.getDerivedProduct());
            }
            else {
                // subscription no longer has a derived product
                existingPool.setDerivedProduct(null);
            }
            existingPool.getDerivedProvidedProducts().clear();
            if (incomingProvided != null && !incomingProvided.isEmpty()) {
                existingPool.getDerivedProvidedProducts().addAll(incomingProvided);
            }
        }
        return productsChanged;
    }

    private boolean checkForDateChange(Date start, Date end, Pool existingPool) {

        boolean datesChanged = (!start.equals(
            existingPool.getStartDate())) ||
            (!end.equals(existingPool.getEndDate()));

        if (datesChanged) {
            existingPool.setStartDate(start);
            existingPool.setEndDate(end);
        }
        return datesChanged;
    }

    private boolean checkForQuantityChange(Subscription sub, Pool existingPool,
        List<Pool> existingPools, Map<String, String> attributes) {

        // Expected quantity is normally the subscription's quantity, but for
        // virt only pools we expect it to be sub quantity * virt_limit:
        long expectedQuantity = calculateQuantity(sub.getQuantity(), sub.getProduct(),
                sub.getUpstreamPoolId());
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
        if (existingPool.hasAttribute("pool_derived") &&
            existingPool.attributeEquals("virt_only", "true") &&
            (attributes.containsKey("virt_limit") || existingPool.getProduct().hasAttribute("virt_limit"))) {

            if (!attributes.containsKey("virt_limit")) {
                log.warn("virt_limit attribute has been removed from subscription, " +
                    "flagging pool for deletion if supported: " + existingPool.getId());
                // virt_limit has been removed! We need to clean up this pool. Set
                // attribute to notify the server of this:
                existingPool.setMarkedForDelete(true);
                // Older candlepin's won't look at the delete indicator, so we will
                // set the expected quantity to 0 to effectively disable the pool
                // on those servers as well.
                expectedQuantity = 0;
            }
            else {
                String virtLimitStr = attributes.get("virt_limit");

                if ("unlimited".equals(virtLimitStr)) {
                    // 0 will only happen if the rules set it to be 0 -- don't modify
                    // -1 for pretty much all the rest
                    expectedQuantity = existingPool.getQuantity() == 0 ?
                        0 : -1;
                }
                else {
                    try {
                        int virtLimit = Integer.parseInt(virtLimitStr);
                        if (config.getBoolean(ConfigProperties.STANDALONE) &&
                            !"true".equals(existingPool.getAttributeValue("unmapped_guests_only"))) {
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
                                    derivedPool.getAttributeValue("pool_derived");
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
