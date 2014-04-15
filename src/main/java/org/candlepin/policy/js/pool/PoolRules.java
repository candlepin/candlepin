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

import org.candlepin.config.Config;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Branding;
import org.candlepin.model.Consumer;
import org.candlepin.model.DerivedProvidedProduct;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.ProductPoolAttribute;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.Subscription;
import org.candlepin.policy.js.ProductCache;

import com.google.inject.Inject;

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
    private ProductCache productCache;
    private Config config;
    private EntitlementCurator entCurator;


    @Inject
    public PoolRules(PoolManager poolManager, ProductCache productCache, Config config,
        EntitlementCurator entCurator) {
        this.poolManager = poolManager;
        this.productCache = productCache;
        this.config = config;
        this.entCurator = entCurator;
    }

    private long calculateQuantity(Subscription sub) {
        long quantity = sub.getQuantity() * sub.getProduct().getMultiplier();

        // In hosted, we increase the quantity on the subscription. However in standalone,
        // we assume this already has happened in hosted and the accurate quantity was
        // exported:
        if (sub.getProduct().hasAttribute("instance_multiplier") &&
            sub.getUpstreamPoolId() == null) {

            int instanceMultiplier = Integer.parseInt(
                sub.getProduct().getAttribute("instance_multiplier").getValue());
            log.debug("Increasing pool quantity for instance multiplier: " +
                instanceMultiplier);
            quantity = quantity * instanceMultiplier;
        }
        return quantity;
    }

    public List<Pool> createPools(Subscription sub) {
        log.info("Creating pools for new subscription: " + sub);
        PoolHelper helper = new PoolHelper(this.poolManager,
            this.productCache, null);

        List<Pool> pools = new LinkedList<Pool>();
        Map<String, String> attributes =
            helper.getFlattenedAttributes(sub.getProduct());
        long quantity = calculateQuantity(sub);
        Set<ProvidedProduct> providedProducts = new HashSet<ProvidedProduct>();
        Set<DerivedProvidedProduct> subProvidedProducts =
            new HashSet<DerivedProvidedProduct>();
        Pool newPool = new Pool(sub.getOwner(), sub.getProduct().getId(),
                sub.getProduct().getName(), providedProducts, quantity, sub.getStartDate(),
                sub.getEndDate(), sub.getContractNumber(), sub.getAccountNumber(),
                sub.getOrderNumber());
        newPool.setDerivedProvidedProducts(subProvidedProducts);

        if (sub.getProvidedProducts() != null) {
            for (Product p : sub.getProvidedProducts()) {
                ProvidedProduct providedProduct = new ProvidedProduct(p.getId(),
                    p.getName());
                providedProduct.setPool(newPool);
                providedProducts.add(providedProduct);
            }
        }

        if (sub.getDerivedProvidedProducts() != null) {
            for (Product p : sub.getDerivedProvidedProducts()) {
                DerivedProvidedProduct providedProduct =
                    new DerivedProvidedProduct(p.getId(), p.getName());
                providedProduct.setPool(newPool);
                subProvidedProducts.add(providedProduct);
            }
        }

        helper.copyProductAttributesOntoPool(sub.getProduct().getId(), newPool);
        if (sub.getDerivedProduct() != null) {
            newPool.setDerivedProductId(sub.getDerivedProduct().getId());
            newPool.setDerivedProductName(sub.getDerivedProduct().getName());
            helper.copySubProductAttributesOntoPool(sub.getDerivedProduct().getId(),
                newPool);
        }

        for (Branding b : sub.getBranding()) {
            newPool.getBranding().add(new Branding(b.getProductId(), b.getType(),
                b.getName()));
        }

        newPool.setSourceSubscription(new SourceSubscription(sub.getId(), "master"));
        ProductAttribute virtAtt = sub.getProduct().getAttribute("virt_only");

        // note: the product attributes are getting copied above, but the following will
        // make virt_only a pool attribute. That makes the pool explicitly virt_only to
        // subscription manager and any other downstream comsumer.
        if (virtAtt != null && virtAtt.getValue() != null &&
            !virtAtt.getValue().equals("")) {
            newPool.addAttribute(new org.candlepin.model.PoolAttribute("virt_only",
                virtAtt.getValue()));
        }

        pools.add(newPool);

        boolean hostLimited = attributes.containsKey("host_limited") &&
            attributes.get("host_limited").equals("true");
        // Check if we need to create a virt-only pool for this subscription:
        if (attributes.containsKey("virt_limit") && !config.standalone() &&
            !hostLimited) {
            HashMap<String, String> virtAttributes = new HashMap<String, String>();
            virtAttributes.put("virt_only", "true");
            virtAttributes.put("pool_derived", "true");
            virtAttributes.put("physical_only", "false");
            // Make sure the virt pool does not have a virt_limit,
            // otherwise this will recurse infinitely
            virtAttributes.put("virt_limit", "0");

            String virtQuantity = getVirtQuantity(attributes.get("virt_limit"), quantity);
            if (virtQuantity != null) {
                Pool derivedPool = helper.createPool(sub, sub.getProduct().getId(),
                                                    virtQuantity, virtAttributes);
                // Using derived here because only one derived pool
                // is created for this subscription
                derivedPool.setSourceSubscription(
                    new SourceSubscription(sub.getId(), "derived"));
                pools.add(derivedPool);
            }
        }
        return pools;
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
     * @param floatingPools ools with no subscription ID
     * @return pool updates
     */
    public List<PoolUpdate> updatePools(List<Pool> floatingPools) {
        List<PoolUpdate> updates = new LinkedList<PoolUpdate>();
        for (Pool p : floatingPools) {

            if (p.getSubscriptionId() != null) {
                // Should be filtered out before calling, but just in case we skip it:
                continue;
            }

            if (p.getSourceStack() != null) {
                Consumer c = p.getSourceStack().getSourceConsumer();
                if (c == null) {
                    log.error("Stack derived pool has no source consumer: " + p.getId());
                }
                else {
                    PoolUpdate update = updatePoolFromStack(p);
                    if (update.changed()) {
                        updates.add(update);
                    }
                }
            }
        }
        return updates;
    }

    public List<PoolUpdate> updatePools(Subscription sub, List<Pool> existingPools) {
        log.debug("Refreshing pools for existing subscription: " + sub);
        log.debug("  existing pools: " + existingPools.size());
        PoolHelper helper = new PoolHelper(this.poolManager, this.productCache, null);

        List<PoolUpdate> poolsUpdated = new LinkedList<PoolUpdate>();
        Map<String, String> attributes =
            helper.getFlattenedAttributes(sub.getProduct());
        for (Pool existingPool : existingPools) {

            log.debug("Checking pool: " + existingPool.getId());

            // Used to track if anything has changed:
            PoolUpdate update = new PoolUpdate(existingPool);

            update.setDatesChanged(checkForDateChange(sub.getStartDate(),
                sub.getEndDate(), existingPool));

            update.setQuantityChanged(
                checkForQuantityChange(sub, existingPool, existingPools, attributes));

            // Checks product name, ID, and provided products. Attributes are handled
            // separately.
            // TODO: should they be separate? ^^
            update.setProductsChanged(
                checkForChangedProducts(sub.getProduct().getId(),
                    sub.getProduct().getName(),
                    getExpectedProvidedProducts(sub, existingPool),
                    existingPool));

            update.setDerivedProductsChanged(
                checkForChangedDerivedProducts(sub, existingPool));

            update.setProductAttributesChanged(checkForProductAttributeChanges(sub,
                helper, existingPool));

            update.setDerivedProductAttributesChanged(
                checkForSubProductAttributeChanges(sub, helper, existingPool));

            update.setOrderChanged(checkForOrderDataChanges(sub, helper,
                existingPool));

            update.setBrandingChanged(checkForBrandingChanges(sub, existingPool));

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
    public PoolUpdate updatePoolFromStack(Pool pool) {
        List<Entitlement> stackedEnts = this.entCurator.findByStackId(
            pool.getSourceConsumer(), pool.getSourceStackId());
        return this.updatePoolFromStackedEntitlements(pool, stackedEnts);
    }

    public PoolUpdate updatePoolFromStackedEntitlements(Pool pool,
            List<Entitlement> stackedEnts) {

        PoolUpdate update = new PoolUpdate(pool);

        // Nothing to do if there were no entitlements found.
        if (stackedEnts.isEmpty()) {
            return update;
        }

        pool.setSourceEntitlement(null);
        pool.setSourceSubscription(null);

        StackedSubPoolValueAccumulator acc =
            new StackedSubPoolValueAccumulator(pool, stackedEnts);

        // Check if the quantity should be changed. If there was no
        // virt limiting entitlement, then we leave the quantity alone,
        // else, we set the quantity to that of the eldest virt limiting
        // entitlement pool.
        Entitlement eldestWithVirtLimit = acc.getEldestWithVirtLimit();
        if (eldestWithVirtLimit != null) {
            // Quantity may have changed, lets see.
            String virtLimit =
                eldestWithVirtLimit.getPool().getProductAttributeValue("virt_limit");

            Long quantity = virtLimit.equalsIgnoreCase("unlimited") ?
                -1L : Long.parseLong(virtLimit);

            if (!quantity.equals(pool.getQuantity())) {
                pool.setQuantity(quantity);
                update.setQuantityChanged(true);
            }
        }

        update.setDatesChanged(checkForDateChange(acc.getStartDate(), acc.getEndDate(),
            pool));

        // We use the "oldest" entitlement as the master for determining values that
        // could have come from the various subscriptions.
        Entitlement eldest = acc.getEldest();
        Pool eldestEntPool = eldest.getPool();
        boolean useDerived = eldestEntPool.getDerivedProductId() != null;
        String prodId = useDerived ?
            eldestEntPool.getDerivedProductId() : eldestEntPool.getProductId();
        String prodName = useDerived ?
            eldestEntPool.getDerivedProductName() : eldestEntPool.getProductName();

        // Check if product ID, name, or provided products have changed.
        update.setProductsChanged(checkForChangedProducts(prodId, prodName,
            acc.getExpectedProvidedProds(), pool));

        // Check if product attributes have changed.
        Set<ProductPoolAttribute> expectedAttrs = acc.getExpectedAttributes();
        if (!pool.getProductAttributes().equals(
            new HashSet<ProductPoolAttribute>(expectedAttrs))) {
            // Make sure each attribute has correct product ID on it,
            // and update the pool.
            pool.getProductAttributes().clear();
            for (ProductPoolAttribute attr : expectedAttrs) {
                attr.setProductId(pool.getProductId());
                pool.addProductAttribute(attr);
            }
            update.setProductAttributesChanged(true);
        }

        if (!StringUtils.equals(eldestEntPool.getContractNumber(),
            pool.getContractNumber()) ||
            !StringUtils.equals(eldestEntPool.getOrderNumber(), pool.getOrderNumber()) ||
            !StringUtils.equals(eldestEntPool.getAccountNumber(),
                pool.getAccountNumber())) {

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

    private boolean checkForProductAttributeChanges(Subscription sub,
        PoolHelper helper, Pool existingPool) {
        boolean prodAttrsChanged = false;
        // Transfer the subscription's sub-product attributes instead if applicable:
        if (existingPool.hasAttribute("pool_derived") && sub.getDerivedProduct() != null) {
            prodAttrsChanged = helper.copyProductAttributesOntoPool(
                sub.getDerivedProduct().getId(), existingPool);
        }
        else {
            prodAttrsChanged = helper.copyProductAttributesOntoPool(
                sub.getProduct().getId(), existingPool);
        }

        return prodAttrsChanged;
    }

    private boolean checkForSubProductAttributeChanges(Subscription sub,
        PoolHelper helper, Pool existingPool) {
        boolean subProdAttrsChanged = false;
        if (!existingPool.hasAttribute("pool_derived") && sub.getDerivedProduct() != null) {
            subProdAttrsChanged = helper.copySubProductAttributesOntoPool(
                sub.getDerivedProduct().getId(), existingPool);
        }
        return subProdAttrsChanged;
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

    private Set<ProvidedProduct> getExpectedProvidedProducts(Subscription sub,
        Pool existingPool) {
        Set<ProvidedProduct> incomingProvided = new HashSet<ProvidedProduct>();
        if (sub.getProvidedProducts() != null) {
            for (Product p : sub.getProvidedProducts()) {
                incomingProvided.add(new ProvidedProduct(p.getId(), p.getName(),
                    existingPool));
            }
        }
        return incomingProvided;
    }

    private boolean checkForChangedProducts(String incomingProductId,
        String incomingProductName, Set<ProvidedProduct> incomingProvided,
        Pool existingPool) {

        boolean productsChanged =
            !incomingProductId.equals(existingPool.getProductId());
        productsChanged = productsChanged ||
            !incomingProductName.equals(existingPool.getProductName());

        // Build expected set of ProvidedProducts and compare:
        Set<ProvidedProduct> currentProvided = existingPool.getProvidedProducts();
        productsChanged = productsChanged || !currentProvided.equals(incomingProvided);

        if (productsChanged) {
            existingPool.setProductId(incomingProductId);
            existingPool.setProductName(incomingProductName);
            existingPool.getProvidedProducts().clear();
            existingPool.getProvidedProducts().addAll(incomingProvided);
        }
        return productsChanged;
    }

    private boolean checkForChangedDerivedProducts(Subscription sub,
        Pool existingPool) {

        boolean productsChanged = false;
        if (sub.getDerivedProduct() != null) {
            productsChanged = !sub.getDerivedProduct().getId().equals(
                existingPool.getDerivedProductId());
            productsChanged = productsChanged ||
                !sub.getDerivedProduct().getName().equals(
                    existingPool.getDerivedProductName());
        }

        // Build expected set of ProvidedProducts and compare:
        Set<DerivedProvidedProduct> currentProvided =
            existingPool.getDerivedProvidedProducts();
        Set<DerivedProvidedProduct> incomingProvided =
            new HashSet<DerivedProvidedProduct>();
        if (sub.getDerivedProvidedProducts() != null) {
            for (Product p : sub.getDerivedProvidedProducts()) {
                incomingProvided.add(new DerivedProvidedProduct(p.getId(), p.getName(),
                    existingPool));
            }
        }
        productsChanged = productsChanged || !currentProvided.equals(incomingProvided);

        if (productsChanged) {
            // 998317: NPE during refresh causes refresh to abort.
            // Above we check getDerivedProduct for null, but here
            // we ignore the fact that it may be null. So we will
            // now check for null to avoid blowing up.
            if (sub.getDerivedProduct() != null) {
                existingPool.setDerivedProductName(sub.getDerivedProduct().getName());
                existingPool.setDerivedProductId(sub.getDerivedProduct().getId());
            }
            else {
                // subscription no longer has a derived product
                existingPool.setDerivedProductName(null);
                existingPool.setDerivedProductId(null);
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
        long expectedQuantity = calculateQuantity(sub);
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
        if (existingPool.hasAttribute("pool_derived") &&
            existingPool.attributeEquals("virt_only", "true") &&
            existingPool.hasProductAttribute("virt_limit")) {

            if (!attributes.containsKey("virt_limit")) {
                log.warn("virt_limit attribute has been removed from subscription, " +
                    "flagging pool for deletion if supported: " + existingPool.getId());
                // virt_limit has been removed! We need to clean up this pool. Set
                // attribute to notify the server of this:
                existingPool.setAttribute("candlepin.delete_pool", "true");
                // Older candlepin's won't look at the delete attribute, so we will
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
                        if (config.standalone()) {
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
