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

import org.candlepin.controller.PoolManager;
import org.candlepin.model.Branding;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.SourceStack;
import org.candlepin.model.SourceSubscription;
import org.candlepin.model.Subscription;
import org.candlepin.policy.js.AttributeHelper;
import org.candlepin.policy.js.ProductCache;

import org.apache.commons.lang.StringUtils;

import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;



/**
 * Post Entitlement Helper, this object is provided as a global variable to the
 * post entitlement javascript functions allowing them to perform a specific set
 * of operations we support.
 */
public class PoolHelper extends AttributeHelper {

    private PoolManager poolManager;
    private ProductCache productCache;
    private Entitlement sourceEntitlement;

    public PoolHelper(PoolManager poolManager, ProductCache productCache, Entitlement sourceEntitlement) {
        this.poolManager = poolManager;
        this.productCache = productCache;
        this.sourceEntitlement = sourceEntitlement;
    }

    /**
     * Create a pool only for virt guests of a particular host consumer.
     *
     * @param product Label of the product the pool is for.
     * @param pool Pool this host restricted pool is being derived from.
     * @param quantity Number of entitlements for this pool, also accepts "unlimited".
     * @return the pool which was created
     */
    public Pool createHostRestrictedPool(Product product, Pool pool, String quantity) {

        Pool consumerSpecificPool = null;
        if (pool.getDerivedProduct() == null) {
            consumerSpecificPool = this.createPool(
                product,
                pool.getOwner(),
                quantity,
                pool.getStartDate(),
                pool.getEndDate(),
                pool.getContractNumber(),
                pool.getAccountNumber(),
                pool.getOrderNumber(),
                pool.getProvidedProducts()
            );
        }
        else {
            // If a derived product is on the pool, we want to define the derived pool
            // with the derived product data that was defined on the parent pool,
            // allowing the derived pool to have different attributes than the parent.
            consumerSpecificPool = this.createPool(
                pool.getDerivedProduct(),
                pool.getOwner(),
                quantity,
                pool.getStartDate(),
                pool.getEndDate(),
                pool.getContractNumber(),
                pool.getAccountNumber(),
                pool.getOrderNumber(),
                pool.getDerivedProvidedProducts()
            );
        }

        consumerSpecificPool.setAttribute("requires_host", sourceEntitlement.getConsumer().getUuid());
        consumerSpecificPool.setAttribute("pool_derived", "true");
        consumerSpecificPool.setAttribute("virt_only", "true");
        consumerSpecificPool.setAttribute("physical_only", "false");

        // If the originating pool is stacked, we want to create the derived pool based on
        // the entitlements in the stack, instead of just the parent pool.
        if (pool.isStacked()) {
            poolManager.updatePoolFromStack(consumerSpecificPool, null);
        }
        else {
            // attribute per 795431, useful for rolling up pool info in headpin
            consumerSpecificPool.setAttribute("source_pool_id", pool.getId());
            consumerSpecificPool.setSourceSubscription(
                new SourceSubscription(
                    pool.getSubscriptionId(),
                    sourceEntitlement.getId()
                )
            );
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
     * Copies the provided products from a subscription to a derived pool.
     *
     * @param source subscription
     * @param destination pool
     */
    private void copyProvidedProducts(Subscription source, Pool destination) {
        Set<Product> products = source.getProvidedProducts();
        // Use derived product data if it exists, as this is a derived bonus pool:
        if (source.getDerivedProvidedProducts() != null &&
                source.getDerivedProvidedProducts().size() > 0) {
            products = source.getDerivedProvidedProducts();
        }
        for (Product providedProduct : products) {
            destination.addProvidedProduct(providedProduct);
        }
    }

    // TODO: Update this method to not use a subscription... somehow.
    public Pool createPool(Subscription sub, Product product, String quantity,
        Map<String, String> attributes) {

        Pool pool = createPool(
            product,
            sub.getOwner(),
            quantity,
            sub.getStartDate(),
            sub.getEndDate(),
            sub.getContractNumber(),
            sub.getAccountNumber(),
            sub.getOrderNumber(),
            new HashSet<Product>()
        );

        pool.setSourceSubscription(new SourceSubscription(sub.getId(), "master"));

        copyProvidedProducts(sub, pool);

        // Add in the new attributes
        for (Entry<String, String> entry : attributes.entrySet()) {
            pool.setAttribute(entry.getKey(), entry.getValue());
        }


        for (Branding b : sub.getBranding()) {
            pool.getBranding().add(new Branding(b.getProductId(), b.getType(),
                b.getName()));
        }
        return pool;
    }

    private Pool createPool(Product product, Owner owner, String quantity, Date startDate,
        Date endDate, String contractNumber, String accountNumber, String orderNumber,
        Set<Product> providedProducts) {

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

        Pool pool = new Pool(
            owner,
            product,
            new HashSet<Product>(),
            q,
            startDate,
            endDate,
            contractNumber,
            accountNumber,
            orderNumber
        );

        // Must be sure to copy the provided products, not try to re-use them directly:
        for (Product pp : providedProducts) {
            pool.addProvidedProduct(pp);
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
