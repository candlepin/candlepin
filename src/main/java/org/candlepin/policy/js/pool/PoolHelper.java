/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.candlepin.bind.PoolOperations;
import org.candlepin.controller.PoolService;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.model.SourceStack;
import org.candlepin.model.SourceSubscription;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;



/**
 * Post Entitlement Helper, and some attribute utility methods.
 */
public class PoolHelper {

    private PoolHelper() {
        // Intentionally left empty
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
        Map<String, String> attributes = new HashMap<>();

        if (pool != null) {
            attributes.putAll(pool.getProductAttributes());
            attributes.putAll(pool.getAttributes());
        }

        return attributes;
    }

    /**
     * Create a pool only for virt guests of a particular host consumer.
     *
     * @param pools Pools these host restricted pools are being derived from.
     * @return pools the created pools
     */
    public static PoolOperations createHostRestrictedPools(PoolService poolService, Consumer consumer,
        List<Pool> pools, Map<String, Entitlement> sourceEntitlements,
        Map<String, Map<String, String>> attributeMaps) {

        PoolOperations poolOperations = new PoolOperations();
        List<Pool> poolsToUpdateFromStack = new ArrayList<>();
        for (Pool pool : pools) {
            Product product = pool.getProduct();
            Product derivedProduct = pool.getDerivedProduct();
            Map<String, String> attributes = attributeMaps.get(pool.getId());
            String quantity = attributes.get("virt_limit");

            Pool consumerSpecificPool = null;

            Entitlement entitlement = sourceEntitlements.get(pool.getId());
            if (derivedProduct == null) {
                consumerSpecificPool = createPool(
                    product,
                    pool.getOwner(),
                    quantity,
                    pool.getStartDate(),
                    pool.getEndDate(),
                    pool.getContractNumber(),
                    pool.getAccountNumber(),
                    pool.getOrderNumber(),
                    entitlement,
                    consumer,
                    pool);
            }
            else {
                // If a derived product is on the pool, we want to define the derived pool with the
                // derived product data that was defined on the parent pool, allowing the derived
                // pool to have different attributes than the parent.
                consumerSpecificPool = createPool(
                    derivedProduct,
                    pool.getOwner(),
                    quantity,
                    pool.getStartDate(),
                    pool.getEndDate(),
                    pool.getContractNumber(),
                    pool.getAccountNumber(),
                    pool.getOrderNumber(),
                    entitlement,
                    consumer,
                    pool);
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

                String subscriptionId = pool.getSubscriptionId();
                if (subscriptionId != null && !subscriptionId.isEmpty()) {
                    consumerSpecificPool.setSourceSubscription(
                        new SourceSubscription(subscriptionId, entitlement.getId()));
                }
            }
            poolOperations.createPool(consumerSpecificPool);
        }

        if (CollectionUtils.isNotEmpty(poolsToUpdateFromStack)) {
            poolService.updatePoolsFromStack(consumer, poolsToUpdateFromStack, null, false);
        }

        return poolOperations;
    }

    public static Pool clonePool(Pool sourcePool, Product product, String quantity,
        Map<String, String> attributes, String subKey,
        Entitlement sourceEntitlement, Consumer sourceConsumer) {

        Pool pool = createPool(product, sourcePool.getOwner(), quantity,
            sourcePool.getStartDate(), sourcePool.getEndDate(),
            sourcePool.getContractNumber(), sourcePool.getAccountNumber(),
            sourcePool.getOrderNumber(), sourceEntitlement,
            sourceConsumer, sourcePool);

        SourceSubscription srcSub = sourcePool.getSourceSubscription();
        if (srcSub != null && srcSub.getSubscriptionId() != null) {
            pool.setSourceSubscription(new SourceSubscription(srcSub.getSubscriptionId(), subKey));
        }

        // Add in the new attributes
        for (Entry<String, String> entry : attributes.entrySet()) {
            pool.setAttribute(entry.getKey(), entry.getValue());
        }

        // Copy upstream fields
        // Impl note/TODO:
        // We are only doing this to facilitate marking pools derived from an upstream source/manifest
        // as also from that same upstream source. A proper pool hierarchy would be a better solution
        // here, but this will work for the interim.
        pool.setUpstreamPoolId(sourcePool.getUpstreamPoolId());
        pool.setUpstreamEntitlementId(sourcePool.getUpstreamEntitlementId());
        pool.setUpstreamConsumerId(sourcePool.getUpstreamConsumerId());

        return pool;
    }

    private static Pool createPool(Product product, Owner owner, String quantity, Date startDate,
        Date endDate, String contractNumber, String accountNumber, String orderNumber,
        Entitlement sourceEntitlement, Consumer sourceConsumer,
        Pool sourcePool) {

        Long q = Pool.parseQuantity(quantity);

        Pool pool = new Pool();
        pool.setOwner(owner);
        pool.setProduct(product);
        pool.setQuantity(q);
        pool.setStartDate(startDate);
        pool.setEndDate(endDate);
        pool.setContractNumber(contractNumber);
        pool.setAccountNumber(accountNumber);
        pool.setOrderNumber(orderNumber);

        if (sourcePool != null) {
            // Ensure the new pool inherits the managed status of the source pool
            pool.setManaged(sourcePool.isManaged());

            if (sourceConsumer != null && sourceEntitlement != null) {
                if (sourcePool.isStacked()) {
                    pool.setSourceStack(new SourceStack(sourceConsumer, sourcePool.getStackId()));
                }
                else {
                    pool.setSourceEntitlement(sourceEntitlement);
                }
            }
        }

        // temp - we need a way to specify this on the product
        pool.setAttribute(Pool.Attributes.REQUIRES_CONSUMER_TYPE, "system");

        return pool;
    }

    public static boolean checkForOrderChanges(Pool existingPool, Pool pool) {
        return (!StringUtils.equals(existingPool.getOrderNumber(), pool.getOrderNumber()) ||
            !StringUtils.equals(existingPool.getAccountNumber(), pool.getAccountNumber()) ||
            !StringUtils.equals(existingPool.getContractNumber(), pool.getContractNumber()));
    }
}
