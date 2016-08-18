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
import org.candlepin.model.Attribute;
import org.candlepin.model.Branding;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerProductCurator;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.SourceStack;
import org.candlepin.model.SourceSubscription;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;



/**
 * Post Entitlement Helper, and some attribute utility methods.
 */
public class PoolHelper {

    private PoolHelper() {
    }

    /**
     * Both products and pools can carry attributes, we need to
     * trigger rules for each. In this map, pool attributes will
     * override product attributes, should the same key be set
     * for both.
     *
     * @param pool Pool can be null.
     * @return Map of all attribute names and values. Pool attributes
     *         have priority.
     */
    public static Map<String, String> getFlattenedAttributes(Pool pool) {
        Map<String, String> allAttributes = new HashMap<String, String>();
        if (pool != null) {
            allAttributes.putAll(getFlattenedAttributes(pool.getProductAttributes()));
            allAttributes.putAll(getFlattenedAttributes(pool.getAttributes()));
        }
        return allAttributes;
    }

    public static Map<String, String> getFlattenedAttributes(Collection<? extends Attribute> attrs) {
        Map<String, String> flattened = new HashMap<String, String>();
        for (Attribute a : attrs) {
            flattened.put(a.getName(), a.getValue());
        }
        return flattened;
    }

    public static Map<String, String> getFlattenedAttributes(Product product) {
        return getFlattenedAttributes(product.getAttributes());
    }

    /**
     * Create a pool only for virt guests of a particular host consumer.
     *
     * @param pools Pools these host restricted pools are being derived from.
     * @return pools the created pools
     */
    public static List<Pool> createHostRestrictedPools(PoolManager poolManager, Consumer consumer,
        List<Pool> pools, Map<String, Entitlement> sourceEntitlements,
        Map<String, Map<String, String>> attributeMaps) {
        List<Pool> poolsToCreate = new ArrayList<Pool>();
        List<Pool> poolsToUpdateFromStack = new ArrayList<Pool>();
        for (Pool pool : pools) {
            Product product = pool.getProduct();
            Pool consumerSpecificPool = null;
            Map<String, String> attributes = attributeMaps.get(pool.getId());
            String quantity = attributes.get("virt_limit");
            if (pool.getDerivedProduct() == null) {
                consumerSpecificPool = createPool(
                        product,
                        pool.getOwner(),
                        quantity,
                        pool.getStartDate(),
                        pool.getEndDate(),
                        pool.getContractNumber(),
                        pool.getAccountNumber(),
                        pool.getOrderNumber(),
                        pool.getProvidedProducts(),
                        sourceEntitlements.get(pool.getId()));
            }
            else {
                // If a derived product is on the pool, we want to define the
                // derived pool
                // with the derived product data that was defined on the parent
                // pool,
                // allowing the derived pool to have different attributes than
                // the parent.
                consumerSpecificPool = createPool(
                        pool.getDerivedProduct(),
                        pool.getOwner(),
                        quantity,
                        pool.getStartDate(),
                        pool.getEndDate(),
                        pool.getContractNumber(),
                        pool.getAccountNumber(),
                        pool.getOrderNumber(),
                        pool.getDerivedProvidedProducts(),
                        sourceEntitlements.get(pool.getId()));
            }

            consumerSpecificPool.setAttribute(Pool.Attributes.REQUIRES_HOST, consumer.getUuid());
            consumerSpecificPool.setAttribute(Pool.Attributes.DERIVED_POOL, "true");
            consumerSpecificPool.setAttribute(Pool.Attributes.VIRT_ONLY, "true");
            consumerSpecificPool.setAttribute(Pool.Attributes.PHYSICAL_ONLY, "false");

            // If the originating pool is stacked, we want to create the derived
            // pool based on the entitlements in the stack, instead of just the
            // parent pool.
            if (pool.isStacked()) {
                poolsToUpdateFromStack.add(consumerSpecificPool);
            }
            else {
                // attribute per 795431, useful for rolling up pool info in headpin
                consumerSpecificPool.setAttribute(Pool.Attributes.SOURCE_POOL_ID, pool.getId());
                consumerSpecificPool.setSourceSubscription(new SourceSubscription(pool.getSubscriptionId(),
                    sourceEntitlements.get(pool.getId()).getId()));
            }
            poolsToCreate.add(consumerSpecificPool);
        }

        if (CollectionUtils.isNotEmpty(poolsToUpdateFromStack)) {
            poolManager.updatePoolsFromStack(consumer, poolsToUpdateFromStack);
        }
        return poolManager.createPools(poolsToCreate);
    }

    /**
     * Copies the provided products from a source pool to a derived pool.
     *
     * @param source subscription
     * @param destination pool
     */
    private static void copyProvidedProducts(Pool source, Pool destination, OwnerProductCurator curator) {
        Set<Product> products;

        if (source.getDerivedProduct() != null) {
            products = source.getDerivedProvidedProducts();
        }
        else {
            products = source.getProvidedProducts();
        }

        for (Product product : products) {
            // If no result is returned here, the product has not been correctly imported
            // into the organization, indicating a problem somewhere in the sync or refresh code:
            Product destprod = curator.getProductById(destination.getOwner(), product.getId());
            if (destprod == null) {
                throw new RuntimeException("Product " + product.getId() +
                    " has not been imported into org " + destination.getOwner().getKey());
            }
            destination.addProvidedProduct(destprod);
        }
    }

    public static Pool clonePool(Pool sourcePool, Product product, String quantity,
        Map<String, String> attributes, String subKey, OwnerProductCurator curator,
        Entitlement sourceEntitlement) {

        Pool pool = createPool(product, sourcePool.getOwner(), quantity,
            sourcePool.getStartDate(), sourcePool.getEndDate(),
            sourcePool.getContractNumber(), sourcePool.getAccountNumber(),
            sourcePool.getOrderNumber(), new HashSet<Product>(), sourceEntitlement);

        pool.setSourceSubscription(
            new SourceSubscription(sourcePool.getSubscriptionId(), subKey));

        copyProvidedProducts(sourcePool, pool, curator);

        // Add in the new attributes
        for (Entry<String, String> entry : attributes.entrySet()) {
            pool.setAttribute(entry.getKey(), entry.getValue());
        }

        for (Branding b : sourcePool.getBranding()) {
            pool.getBranding().add(new Branding(b.getProductId(), b.getType(),
                b.getName()));
        }
        return pool;
    }

    private static Pool createPool(Product product, Owner owner, String quantity, Date startDate,
        Date endDate, String contractNumber, String accountNumber, String orderNumber,
        Set<Product> providedProducts, Entitlement sourceEntitlement) {

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
        pool.setAttribute(Pool.Attributes.REQUIRES_CONSUMER_TYPE, "system");

        return pool;
    }

    public static boolean checkForOrderChanges(Pool existingPool, Pool pool) {
        return (!StringUtils.equals(existingPool.getOrderNumber(), pool.getOrderNumber()) ||
                !StringUtils.equals(existingPool.getAccountNumber(), pool.getAccountNumber()) || !StringUtils
                    .equals(existingPool.getContractNumber(), pool.getContractNumber()));
    }
}
