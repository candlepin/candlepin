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

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.candlepin.controller.PoolManager;
import org.candlepin.model.Attribute;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductPoolAttribute;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.DerivedProductPoolAttribute;
import org.candlepin.model.DerivedProvidedProduct;
import org.candlepin.model.SourceStack;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.Subscription;
import org.candlepin.policy.js.AttributeHelper;
import org.candlepin.policy.js.ProductCache;

/**
 * Post Entitlement Helper, this object is provided as a global variable to the
 * post entitlement javascript functions allowing them to perform a specific set
 * of operations we support.
 */
public class PoolHelper extends AttributeHelper {

    private PoolManager poolManager;
    private ProductCache productCache;
    private Entitlement sourceEntitlement;

    public PoolHelper(PoolManager poolManager, ProductCache productCache,
        Entitlement sourceEntitlement) {
        this.poolManager = poolManager;
        this.productCache = productCache;
        this.sourceEntitlement = sourceEntitlement;
    }

    /**
     * Create a pool only for virt guests of a particular host consumer.
     *
     *
     *
     * @param productId Label of the product the pool is for.
     * @param pool Pool this host restricted pool is being derived from.
     * @param quantity Number of entitlements for this pool, also accepts "unlimited".
     * @return the pool which was created
     */
    public Pool createHostRestrictedPool(String productId, Pool pool,
        String quantity) {

        Pool consumerSpecificPool = null;
        if (pool.getDerivedProductId() == null) {
            consumerSpecificPool = createPool(productId, pool.getOwner(),
                quantity, pool.getStartDate(), pool.getEndDate(),
                pool.getContractNumber(), pool.getAccountNumber(), pool.getOrderNumber(),
                pool.getProvidedProducts());
        }
        else {
            // If a sub product id is on the pool, we want to define the sub pool
            // with the sub product data that was defined on the parent pool,
            // allowing the sub pool to have different attributes than the parent.
            Set<ProvidedProduct> providedProducts = new HashSet<ProvidedProduct>();
            for (DerivedProvidedProduct subProvided : pool.getDerivedProvidedProducts()) {
                providedProducts.add(new ProvidedProduct(subProvided.getProductId(),
                                                         subProvided.getProductName()));
            }

            consumerSpecificPool = createPool(pool.getDerivedProductId(), pool.getOwner(),
                quantity, pool.getStartDate(), pool.getEndDate(),
                pool.getContractNumber(), pool.getAccountNumber(), pool.getOrderNumber(),
                providedProducts);

        }

        consumerSpecificPool.setAttribute("requires_host",
            sourceEntitlement.getConsumer().getUuid());
        consumerSpecificPool.setAttribute("pool_derived", "true");
        consumerSpecificPool.setAttribute("virt_only", "true");
        consumerSpecificPool.setAttribute("physical_only", "false");

        this.copyProductAttributesOntoPool(consumerSpecificPool.getProductId(),
            consumerSpecificPool);

        // If the originating pool is stacked, we want to create the derived pool based on
        // the entitlements in the stack, instead of just the parent pool.
        if (pool.isStacked()) {
            poolManager.updatePoolFromStack(consumerSpecificPool);
        }
        else {
            // attribute per 795431, useful for rolling up pool info in headpin
            consumerSpecificPool.setAttribute("source_pool_id", pool.getId());
            consumerSpecificPool.setSourceSubscription(
                new SourceSubscription(pool.getSubscriptionId(),
                    sourceEntitlement.getId()));
        }

        poolManager.createPool(consumerSpecificPool);
        return consumerSpecificPool;
    }

    /**
     * Retrieve all pools with the subscription id.
     *
     * @param id Subscription Id for cross-reference.
     * @return list of found pools
     */
    public List<Pool> lookupBySubscriptionId(String id) {
        return poolManager.lookupBySubscriptionId(id);
    }

    /**
     * Update count for a pool.
     *
     * @param pool The pool.
     * @param adjust the amount to adjust (+/-)
     * @return pool
     */
    public Pool updatePoolQuantity(Pool pool, int adjust) {
        return poolManager.updatePoolQuantity(pool, adjust);
    }

    /**
     * Set count for a pool.
     *
     * @param pool The pool.
     * @param set the long amount to set
     * @return pool
     */
    public Pool setPoolQuantity(Pool pool, long set) {
        return poolManager.setPoolQuantity(pool, set);
    }

    /**
     * Copies the provided products from a subscription to a pool.
     *
     * @param source subscription
     * @param destination pool
     */
    public void copyProvidedProducts(Subscription source, Pool destination) {
        for (Product providedProduct : source.getProvidedProducts()) {
            destination.addProvidedProduct(new ProvidedProduct(providedProduct.getId(),
                providedProduct.getName()));
        }

    }

    public Pool createPool(Subscription sub, String productId,
            String quantity, Map<String, String> newPoolAttributes) {

        Pool pool = createPool(productId, sub.getOwner(), quantity, sub.getStartDate(),
            sub.getEndDate(), sub.getContractNumber(), sub.getAccountNumber(),
            sub.getOrderNumber(), new HashSet<ProvidedProduct>());
        pool.setSourceSubscription(new SourceSubscription(sub.getId(), "master"));

        copyProvidedProducts(sub, pool);

        // Add in the new attributes
        for (Entry<String, String> entry : newPoolAttributes.entrySet()) {
            pool.setAttribute(entry.getKey(), entry.getValue());
        }

        copyProductAttributesOntoPool(productId, pool);

        return pool;
    }

    /**
     * Copies all of the {@link Products} attributes onto the pool.
     * If an attribute already exists, it will be updated. Any attributes that are
     * on the {@link Pool} but not on the {@link Product} will be removed.
     *
     * @param productId
     * @param pool
     *
     * @return true if the pools attributes changed, false otherwise.
     */
    protected boolean copyProductAttributesOntoPool(String productId, Pool pool) {
        boolean hasChanged = false;
        Product product = productCache.getProductById(productId);

        // Build a set of what we would expect and compare them to the current:
        Set<ProductPoolAttribute> currentAttrs = pool.getProductAttributes();
        Set<ProductPoolAttribute> incomingAttrs = new HashSet<ProductPoolAttribute>();
        if (product != null) {
            for (Attribute attr : product.getAttributes()) {
                ProductPoolAttribute newAttr = new ProductPoolAttribute(attr.getName(),
                    attr.getValue(), product.getId());
                newAttr.setPool(pool);
                incomingAttrs.add(newAttr);
            }
        }

        if (!currentAttrs.equals(incomingAttrs)) {
            hasChanged = true;
            pool.getProductAttributes().clear();
            pool.getProductAttributes().addAll(incomingAttrs);
        }

        return hasChanged;
    }

    protected boolean copySubProductAttributesOntoPool(String productId, Pool pool) {
        boolean hasChanged = false;
        Product product = productCache.getProductById(productId);

        // Build a set of what we would expect and compare them to the current:
        Set<DerivedProductPoolAttribute> currentAttrs = pool.getDerivedProductAttributes();
        Set<DerivedProductPoolAttribute> incomingAttrs =
            new HashSet<DerivedProductPoolAttribute>();
        if (product != null) {
            for (Attribute attr : product.getAttributes()) {
                DerivedProductPoolAttribute newAttr = new DerivedProductPoolAttribute(
                    attr.getName(), attr.getValue(), product.getId());
                newAttr.setPool(pool);
                incomingAttrs.add(newAttr);
            }
        }

        if (!currentAttrs.equals(incomingAttrs)) {
            hasChanged = true;
            pool.getDerivedProductAttributes().clear();
            pool.getDerivedProductAttributes().addAll(incomingAttrs);
        }

        return hasChanged;
    }

    private Pool createPool(String productId, Owner owner, String quantity, Date startDate,
        Date endDate, String contractNumber, String accountNumber, String orderNumber,
        Set<ProvidedProduct> providedProducts) {

        Long q = null;
        if (quantity.equalsIgnoreCase("unlimited")) {
            q = -1L;
        }
        else {
            try {
                q = Long.parseLong(quantity);
            }
            catch (NumberFormatException e) {
                q = 0L;
            }
        }

        Product derivedProduct = productCache.getProductById(productId);
        Pool pool = new Pool(owner, productId,
            derivedProduct.getName(),
            new HashSet<ProvidedProduct>(), q,
            startDate, endDate,
            contractNumber, accountNumber, orderNumber);

        // Must be sure to copy the provided products, not try to re-use them directly:
        for (ProvidedProduct pp : providedProducts) {
            pool.addProvidedProduct(
                new ProvidedProduct(pp.getProductId(), pp.getProductName()));
        }

        if (sourceEntitlement != null && sourceEntitlement.getPool() != null) {
            if (sourceEntitlement.getPool().isStacked()) {
                pool.setSourceStack(new SourceStack(sourceEntitlement.getConsumer(),
                    sourceEntitlement.getPool().getStackId()));
            }
            else {
                pool.setSourceEntitlement(sourceEntitlement);
            }
        }

        // temp - we need a way to specify this on the product
        pool.setAttribute("requires_consumer_type", "system");

        return pool;
    }

    public boolean checkForOrderChanges(Pool existingPool, Subscription sub) {
        return (!StringUtils.equals(existingPool.getOrderNumber(), sub.getOrderNumber()) ||
            !StringUtils.equals(existingPool.getAccountNumber(), sub.getAccountNumber()) ||
            !StringUtils.equals(existingPool.getContractNumber(), sub.getContractNumber()));
    }
}
