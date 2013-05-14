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
        Pool newPool = new Pool(sub.getOwner(), sub.getProduct().getId(),
                sub.getProduct().getName(), providedProducts, quantity, sub.getStartDate(),
                sub.getEndDate(), sub.getContractNumber(), sub.getAccountNumber(),
                sub.getOrderNumber());

        if (sub.getProvidedProducts() != null) {
            for (Product p : sub.getProvidedProducts()) {
                ProvidedProduct providedProduct = new ProvidedProduct(p.getId(),
                    p.getName());
                providedProduct.setPool(newPool);
                providedProducts.add(providedProduct);
            }
        }
        helper.copyProductAttributesOntoPool(sub, newPool);
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
                        // FIXME Not sure I like the cast to int here. Anything we can do?
                        Pool derivedPool = helper.createPool(sub, sub.getProduct().getId(),
                            Integer.toString((int) virtQuantity), virtAttributes);
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
            boolean datesChanged = (!sub.getStartDate().equals(
                existingPool.getStartDate())) ||
                (!sub.getEndDate().equals(existingPool.getEndDate()));

            // Expected quantity is normally the subscription's quantity, but for
            // virt only pools we expect it to be sub quantity * virt_limit:
            long expectedQuantity = calculateQuantity(sub);

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
                            continue;
                        }
                    }
                }
            }

            boolean quantityChanged = !(expectedQuantity == existingPool.getQuantity());
            boolean productsChanged = helper.checkForChangedProducts(existingPool, sub);

            boolean prodAttrsChanged = helper.copyProductAttributesOntoPool(sub,
                existingPool);
            boolean orderDataChanged = helper.checkForOrderChanges(existingPool, sub);

            if (prodAttrsChanged) {
                log.info("Updated product attributes from subscription.");
            }

            if (!(quantityChanged || datesChanged || productsChanged ||
                  prodAttrsChanged || orderDataChanged)) {
                //TODO: Should we check whether pool is overflowing here?
                log.info("   No updates required.");
                continue;
            }

            if (quantityChanged) {
                this.updateQuantityChanged(existingPool, expectedQuantity);
            }

            if (orderDataChanged) {
                this.updateOrderChanged(existingPool, sub);
            }

            if (datesChanged) {
                this.updateDatesChanged(existingPool, sub);
            }

            if (productsChanged) {
                this.updateProductsChanged(existingPool, sub);
            }
            poolsUpdated.add(new org.candlepin.policy.js.pool.PoolUpdate(existingPool,
                datesChanged, quantityChanged, productsChanged, orderDataChanged));
        }

        return poolsUpdated;
    }

    protected void updateQuantityChanged(Pool existingPool, Long expectedQuantity) {
        log.info("   Quantity changed to: " + expectedQuantity);
        existingPool.setQuantity(expectedQuantity);
    }

    protected void updateOrderChanged(Pool existingPool, Subscription sub) {
        log.info("   Order Data Changed");
        existingPool.setAccountNumber(sub.getAccountNumber());
        existingPool.setOrderNumber(sub.getOrderNumber());
        existingPool.setContractNumber(sub.getContractNumber());
    }

    protected void updateDatesChanged(Pool existingPool, Subscription sub) {
        log.info("   Subscription dates changed.");
        existingPool.setStartDate(sub.getStartDate());
        existingPool.setEndDate(sub.getEndDate());
    }

    protected void updateProductsChanged(Pool existingPool, Subscription sub) {
        log.info("   Subscription products changed.");
        existingPool.setProductName(sub.getProduct().getName());
        existingPool.setProductId(sub.getProduct().getId());
        existingPool.getProvidedProducts().clear();

        if (sub.getProvidedProducts() != null) {
            for (Product p : sub.getProvidedProducts()) {
                existingPool.addProvidedProduct(new ProvidedProduct(p.getId(),
                    p.getName()));
            }
        }
    }
}
