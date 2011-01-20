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
package org.fedoraproject.candlepin.policy.js.entitlement;

import java.util.HashSet;
import java.util.Set;

import org.fedoraproject.candlepin.controller.PoolManager;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProvidedProduct;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;

/**
 * Post Entitlement Helper, this object is provided as a global variable to the
 * post entitlement javascript functions allowing them to perform a specific set
 * of operations we support.
 */
public class PostEntHelper {
    
    private Entitlement ent;
    private PoolManager poolManager;
    private ProductServiceAdapter prodAdapter;
    
    public PostEntHelper(PoolManager poolManager, ProductServiceAdapter prodAdapter,
        Entitlement e) {
        this.poolManager = poolManager;
        this.ent = e;
        this.prodAdapter = prodAdapter;
    }
    
    /**
    * Create a pool for a product and limit it to consumers a particular user has
    * registered.
    *
    * @param productId Label of the product the pool is for.
    * @param quantity Number of entitlements for this pool, also accepts "unlimited".
    */
    public void createUserRestrictedPool(String productId, 
        Set<ProvidedProduct> providedProducts, 
        String quantity) {

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
        Consumer c = ent.getConsumer();
        Set<ProvidedProduct> providedProductCopies = new HashSet<ProvidedProduct>();
        providedProductCopies.addAll(providedProducts);
        Product derivedProduct = prodAdapter.getProductById(productId);
        Pool consumerSpecificPool = new Pool(c.getOwner(), productId,
            derivedProduct.getName(),
            providedProductCopies, q,
            ent.getPool().getStartDate(), ent.getPool().getEndDate(),
            ent.getPool().getContractNumber(), ent.getPool().getAccountNumber());
        consumerSpecificPool.setRestrictedToUsername(c.getUsername());
        consumerSpecificPool.setSourceEntitlement(ent);
        
        // temp - we need a way to specify this on the product
        consumerSpecificPool.setAttribute("requires_consumer_type", "system");
        
        poolManager.createPool(consumerSpecificPool);
    }

}
