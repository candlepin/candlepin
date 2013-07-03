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

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.log4j.Logger;
import org.candlepin.config.Config;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.DerivedProvidedProduct;
import org.candlepin.model.Subscription;
import org.candlepin.policy.js.ProductCache;

import com.google.inject.Inject;

/**
 * Rules for creation and updating of pools during a refresh pools operation.
 */
public class PoolRules {

    private static Logger log = Logger.getLogger(PoolRules.class);

    private PoolManager poolManager;
    private ProductCache productCache;
    private Config config;


    @Inject
    public PoolRules(PoolManager poolManager, ProductCache productCache, Config config) {
        this.poolManager = poolManager;
        this.productCache = productCache;
        this.config = config;
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
            log.info("Increasing pool quantity for instance multiplier: " +
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
        newPool.setSubscriptionId(sub.getId());
        newPool.setSubscriptionSubKey("master");
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
            // Make sure the virt pool does not have a virt_limit,
            // otherwise this will recurse infinitely
            virtAttributes.put("virt_limit", "0");

            String virtLimit = attributes.get("virt_limit");
            if ("unlimited".equals(virtLimit)) {
                Pool derivedPool = helper.createPool(sub, sub.getProduct().getId(),
                                                    "unlimited", virtAttributes);
                derivedPool.setSubscriptionSubKey("derived");
                pools.add(derivedPool);
            }
            else {
                try {
                    int virtLimitQuantity = Integer.parseInt(virtLimit);
                    if (virtLimitQuantity > 0) {
                        long virtQuantity = quantity * virtLimitQuantity;
                        Pool derivedPool = helper.createPool(sub, sub.getProduct().getId(),
                            String.valueOf(virtQuantity), virtAttributes);
                        derivedPool.setSubscriptionSubKey("derived");
                        pools.add(derivedPool);
                    }
                }
                catch (NumberFormatException nfe) {
                    // Nothing to update if we get here.
                    log.warn("Invalid virt_limit attribute specified.");
                }
            }
        }
        return pools;
    }

    public List<PoolUpdate> updatePools(Subscription sub, List<Pool> existingPools) {
        log.info("Refreshing pools for existing subscription: " + sub);
        log.info("  existing pools: " + existingPools.size());
        PoolHelper helper = new PoolHelper(this.poolManager, this.productCache, null);

        List<PoolUpdate> poolsUpdated = new LinkedList<PoolUpdate>();
        Map<String, String> attributes =
            helper.getFlattenedAttributes(sub.getProduct());
        for (Pool existingPool : existingPools) {

            log.info("Updating pool: " + existingPool.getId());

            // Used to track if anything has changed:
            PoolUpdate update = new PoolUpdate(existingPool);

            update.setDatesChanged(checkForDateChange(sub, existingPool));
            update.setQuantityChanged(
                checkForQuantityChange(sub, existingPool, existingPools, attributes));


            // Checks product name, ID, and provided products. Attributes are handled
            // separately.
            // TODO: should they be separate? ^^
            update.setProductsChanged(
                checkForChangedProducts(sub, existingPool));

            update.setDerivedProductsChanged(
                checkForChangedSubProducts(sub, existingPool));

            update.setProductAttributesChanged(checkForProductAttributeChanges(sub,
                helper, existingPool));
            update.setDerivedProductAttributesChanged(
                checkForSubProductAttributeChanges(sub, helper, existingPool));

            update.setOrderChanged(checkForOrderDataChanges(sub, helper,
                existingPool));

            // All done, see if we found any changes and return an update object if so:
            if (update.changed()) {
                poolsUpdated.add(update);
            }
            else {
                log.info("   No updates required.");
            }
        }

        return poolsUpdated;
    }

    private boolean checkForOrderDataChanges(Subscription sub,
        PoolHelper helper, Pool existingPool) {
        boolean orderDataChanged = helper.checkForOrderChanges(existingPool, sub);
        if (orderDataChanged) {
            log.info("   Order Data Changed");
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
        if (prodAttrsChanged) {
            log.info("Updated product attributes from subscription.");
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
        if (subProdAttrsChanged) {
            log.info("Updated sub-product attributes from subscription.");
        }
        return subProdAttrsChanged;
    }

    private boolean checkForChangedProducts(Subscription sub, Pool existingPool) {

        boolean productsChanged =
            !sub.getProduct().getId().equals(existingPool.getProductId());
        productsChanged = productsChanged ||
            !sub.getProduct().getName().equals(existingPool.getProductName());

        // Build expected set of ProvidedProducts and compare:
        Set<ProvidedProduct> currentProvided = existingPool.getProvidedProducts();
        Set<ProvidedProduct> incomingProvided = new HashSet<ProvidedProduct>();
        if (sub.getProvidedProducts() != null) {
            for (Product p : sub.getProvidedProducts()) {
                incomingProvided.add(new ProvidedProduct(p.getId(), p.getName(),
                    existingPool));
            }
        }
        productsChanged = productsChanged || !currentProvided.equals(incomingProvided);

        if (productsChanged) {
            log.info("   Subscription products changed.");
            existingPool.setProductName(sub.getProduct().getName());
            existingPool.setProductId(sub.getProduct().getId());
            existingPool.getProvidedProducts().clear();
            existingPool.getProvidedProducts().addAll(incomingProvided);
        }
        return productsChanged;
    }

    private boolean checkForChangedSubProducts(Subscription sub,
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
            log.info("   Subscription sub-products changed.");
            existingPool.setDerivedProductName(sub.getDerivedProduct().getName());
            existingPool.setDerivedProductId(sub.getDerivedProduct().getId());
            existingPool.getDerivedProvidedProducts().clear();
            existingPool.getDerivedProvidedProducts().addAll(incomingProvided);
        }
        return productsChanged;
    }

    private boolean checkForDateChange(Subscription sub, Pool existingPool) {

        boolean datesChanged = (!sub.getStartDate().equals(
            existingPool.getStartDate())) ||
            (!sub.getEndDate().equals(existingPool.getEndDate()));

        if (datesChanged) {
            log.info("   Subscription dates changed.");
            existingPool.setStartDate(sub.getStartDate());
            existingPool.setEndDate(sub.getEndDate());
        }
        return datesChanged;
    }

    private boolean checkForQuantityChange(Subscription sub, Pool existingPool,
        List<Pool> existingPools, Map<String, String> attributes) {

        // Expected quantity is normally the subscription's quantity, but for
        // virt only pools we expect it to be sub quantity * virt_limit:
        long expectedQuantity = calculateQuantity(sub);
        expectedQuantity = processHostedVirtLimitPools(existingPools,
            attributes, existingPool, expectedQuantity);


        boolean quantityChanged = !(expectedQuantity == existingPool.getQuantity());

        if (quantityChanged) {
            log.info("   Quantity changed to: " + expectedQuantity);
            existingPool.setQuantity(expectedQuantity);
        }

        return quantityChanged;
    }

    private long processHostedVirtLimitPools(List<Pool> existingPools,
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
