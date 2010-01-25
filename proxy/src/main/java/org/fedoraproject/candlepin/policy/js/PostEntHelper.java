package org.fedoraproject.candlepin.policy.js;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.EntitlementPoolCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductCurator;

/**
 * Post Entitlement Helper, this object is provided as a global variable to the 
 * post entitlement javascript functions allowing them to perform a specific set
 * of operations we support.
 */
public class PostEntHelper {

    EntitlementPoolCurator epCurator;
    ProductCurator productCurator;
    Entitlement ent;
    
    public PostEntHelper(EntitlementPoolCurator epCurator, 
            ProductCurator productCurator, Entitlement ent) {
        this.epCurator = epCurator;
        this.ent = ent;
        this.productCurator = productCurator;
        
    }
    
    /**
     * Create an entitlement pool for a product and limit it to a particular consumer.
     * 
     * @param productLabel Label of the product the pool is for.
     * @param quantity Number of entitlements for this pool. Use a negative number for
     *                 an unlimited pool.
     */
    public void createConsumerPool(String productLabel, Long quantity) {
        Consumer c = ent.getConsumer();
        
        Product p = productCurator.lookupByLabel(productLabel);
        if (p == null) {
            throw new RuleExecutionException("No such product: " + productLabel);
        }
        
        EntitlementPool consumerSpecificPool = new EntitlementPool(c.getOwner(), 
                p, quantity, ent.getPool().getStartDate(), ent.getPool().getEndDate());
        consumerSpecificPool.setConsumer(c);
        consumerSpecificPool.setSourceEntitlement(ent);
        epCurator.create(consumerSpecificPool);
    }
    
}
