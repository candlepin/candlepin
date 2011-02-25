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
package org.fedoraproject.candlepin.policy.js.pool;

import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.fedoraproject.candlepin.controller.PoolManager;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProvidedProduct;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;

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
            String quantity, Map<String, String> attributes) {

        Pool pool = createPool(productId, sub.getOwner(), quantity, sub.getStartDate(),
            sub.getEndDate(), sub.getContractNumber(), sub.getAccountNumber(),
            new HashSet<ProvidedProduct>());
        pool.setSubscriptionId(sub.getId());

        copyProvidedProducts(sub, pool);

        // Add in the attributes
        for (Entry<String, String> entry : attributes.entrySet()) {
            pool.setAttribute(entry.getKey(), entry.getValue());
        }

        return pool;
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

}
