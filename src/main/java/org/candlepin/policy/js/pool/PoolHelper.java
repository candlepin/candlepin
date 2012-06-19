/**
 * Copyright (c) 2009 Red Hat, Inc.
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.candlepin.controller.PoolManager;
import org.candlepin.model.Attribute;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.ProductAttribute;
import org.candlepin.model.ProductPoolAttribute;
import org.candlepin.model.ProvidedProduct;
import org.candlepin.model.Subscription;
import org.candlepin.service.ProductServiceAdapter;

/**
 * Post Entitlement Helper, this object is provided as a global variable to the
 * post entitlement javascript functions allowing them to perform a specific set
 * of operations we support.
 */
public class PoolHelper {

    private PoolManager poolManager;
    private ProductServiceAdapter prodAdapter;
    private Entitlement sourceEntitlement;

    public PoolHelper(PoolManager poolManager, ProductServiceAdapter prodAdapter,
        Entitlement sourceEntitlement) {
        this.poolManager = poolManager;
        this.prodAdapter = prodAdapter;
        this.sourceEntitlement = sourceEntitlement;
    }

    /**
    * Create a pool for a product and limit it to consumers a particular user has
    * registered.
    *
    * @param productId Label of the product the pool is for.
    * @param quantity Number of entitlements for this pool, also accepts "unlimited".
    */
    public void createUserRestrictedPool(String productId, Pool pool,
        String quantity) {

        Pool consumerSpecificPool = createPool(productId, pool.getOwner(), quantity,
            pool.getStartDate(), pool.getEndDate(), pool.getContractNumber(),
            pool.getAccountNumber(), pool.getProvidedProducts());

        consumerSpecificPool.setRestrictedToUsername(
                this.sourceEntitlement.getConsumer().getUsername());

        poolManager.createPool(consumerSpecificPool);
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

        Pool consumerSpecificPool = createPool(productId, pool.getOwner(), quantity,
            pool.getStartDate(), pool.getEndDate(), pool.getContractNumber(),
            pool.getAccountNumber(), pool.getProvidedProducts());

        consumerSpecificPool.setAttribute("requires_host",
            sourceEntitlement.getConsumer().getUuid());
        consumerSpecificPool.setAttribute("pool_derived", "true");
        consumerSpecificPool.setAttribute("virt_only", "true");

        // attribute per 795431, useful for rolling up pool info in headpin
        consumerSpecificPool.setAttribute("source_pool_id", pool.getId());


        consumerSpecificPool.setSubscriptionId(pool.getSubscriptionId());
        this.copyProductIDAttributesOntoPool(productId, consumerSpecificPool);
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
            new HashSet<ProvidedProduct>());
        pool.setSubscriptionId(sub.getId());

        copyProvidedProducts(sub, pool);

        // Add in the new attributes
        for (Entry<String, String> entry : newPoolAttributes.entrySet()) {
            pool.setAttribute(entry.getKey(), entry.getValue());
        }

        copyProductIDAttributesOntoPool(productId, pool);

        return pool;
    }


    /**
     * Copies all of a {@link Subscription}'s top-level product attributes onto the pool.
     * If an attribute already exists, it will be updated. Any attributes that are
     * on the {@link Pool} but not on the {@link Product} will be removed.
     *
     * @param sub
     * @param pool
     *
     * @return true if the pools attributes changed, false otherwise.
     */
    public boolean copyProductAttributesOntoPool(Subscription sub, Pool pool) {
        return this.copyProductIDAttributesOntoPool(sub.getProduct().getId(), pool);
    }


    /**
     * Copies all of a {@link Products}'s top-level product attributes onto the pool.
     * If an attribute already exists, it will be updated. Any attributes that are
     * on the {@link Pool} but not on the {@link Product} will be removed.
     *
     * @param productId
     * @param pool
     *
     * @return true if the pools attributes changed, false otherwise.
     */
    protected boolean copyProductIDAttributesOntoPool(String productId, Pool pool) {
        Set<String> processed = new HashSet<String>();

        boolean hasChanged = false;
        Product product = prodAdapter.getProductById(productId);
        if (product != null) {
            for (Attribute attr : product.getAttributes()) {

                String attributeName = attr.getName();
                String attributeValue = attr.getValue();

                // Add to the processed list so that we can determine which should
                // be removed later.
                processed.add(attributeName);

                if (pool.hasProductAttribute(attributeName) &&
                    attributeValue != null) {
                    ProductPoolAttribute provided =
                        pool.getProductAttribute(attributeName);
                    String providedValue = provided.getValue();
                    if (providedValue != null) {
                        boolean productsAreSame =
                            product.getId().equals(provided.getProductId());
                        boolean attrValueSame = attributeValue.equals(providedValue);
                        if (productsAreSame && attrValueSame) {
                            continue;
                        }
                    }
                }

                // Change detected - update the attribute
                pool.setProductAttribute(attributeName, attributeValue,
                    product.getId());
                hasChanged = true;
            }

            // Determine if any should be removed.
            Set<ProductPoolAttribute> toRemove =
                new HashSet<ProductPoolAttribute>();
            for (ProductPoolAttribute toCheck : pool.getProductAttributes()) {
                if (!processed.contains(toCheck.getName())) {
                    toRemove.add(toCheck);
                    hasChanged = true;
                }
            }
            pool.getProductAttributes().removeAll(toRemove);
        }
        return hasChanged;
    }

    private Pool createPool(String productId, Owner owner, String quantity, Date startDate,
        Date endDate, String contractNumber, String accountNumber,
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

        Product derivedProduct = prodAdapter.getProductById(productId);
        Pool pool = new Pool(owner, productId,
            derivedProduct.getName(),
            new HashSet<ProvidedProduct>(), q,
            startDate, endDate,
            contractNumber, accountNumber);

        // Must be sure to copy the provided products, not try to re-use them directly:
        for (ProvidedProduct pp : providedProducts) {
            pool.addProvidedProduct(
                new ProvidedProduct(pp.getProductId(), pp.getProductName()));
        }

        pool.setSourceEntitlement(sourceEntitlement);

        // temp - we need a way to specify this on the product
        pool.setAttribute("requires_consumer_type", "system");

        return pool;
    }

    public boolean checkForChangedProducts(Pool existingPool, Subscription sub) {
        Set<String> poolProducts = new HashSet<String>();
        Set<String> subProducts = new HashSet<String>();
        poolProducts.add(existingPool.getProductId());

        for (ProvidedProduct pp : existingPool.getProvidedProducts()) {
            poolProducts.add(pp.getProductId());
        }

        subProducts.add(sub.getProduct().getId());
        for (Product product : sub.getProvidedProducts()) {
            subProducts.add(product.getId());
        }

        boolean changeFound = false;
        // Check if the product name has been changed:
        changeFound = !poolProducts.equals(subProducts) ||
            !existingPool.getProductName().equals(sub.getProduct().getName());

        // Check the attributes only when no other change was detected.
        if (!changeFound) {
            changeFound = haveAttributesChanged(existingPool, sub);
        }

        return changeFound;
    }

    private boolean haveAttributesChanged(Pool existing, Subscription sub) {
        Set<ProductPoolAttribute> attribs =
            existing.getProductAttributes();

        // should probably make this part of Pool.
        Map<String, List<ProductPoolAttribute>> byProductId =
            new HashMap<String, List<ProductPoolAttribute>>();

        for (ProductPoolAttribute attrib : attribs) {
            List<ProductPoolAttribute> attribList =
                byProductId.get(attrib.getProductId());

            if (attribList == null) {
                attribList = new LinkedList<ProductPoolAttribute>();
                attribList.add(attrib);
                byProductId.put(attrib.getProductId(), attribList);
            }
            else {
                attribList.add(attrib);
            }
        }

        for (Product product : sub.getProvidedProducts()) {
            List<ProductPoolAttribute> attribList =
                byProductId.get(product.getId());

            if (attribList == null) {
                break;
            }

            for (ProductPoolAttribute attrib : attribList) {
                ProductAttribute pa = product.getAttribute(attrib.getName());
                if (!pa.getValue().equals(attrib.getValue())) {
                    // we found a change, no need to look any further
                    return true;
                }
            }
        }

        return false;
    }

}
