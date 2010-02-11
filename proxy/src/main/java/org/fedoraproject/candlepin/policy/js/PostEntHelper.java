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
package org.fedoraproject.candlepin.policy.js;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.EntitlementPoolCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductCurator;
import org.fedoraproject.candlepin.service.ProductServiceAdapter;

import com.google.inject.Inject;

/**
 * Post Entitlement Helper, this object is provided as a global variable to the 
 * post entitlement javascript functions allowing them to perform a specific set
 * of operations we support.
 */
public class PostEntHelper {

    EntitlementPoolCurator epCurator;
    ProductServiceAdapter prodAdapter;
    Entitlement ent;
    
    @Inject
    public PostEntHelper(EntitlementPoolCurator epCurator, 
            ProductServiceAdapter prodAdapter) {
        this.epCurator = epCurator;
        this.prodAdapter = prodAdapter;
    }
    
    /**
     * Separated from constructor because these objects are not something Guice
     * can inject. Must be called before the post helper is passed in to the Javascript
     * engine.
     * 
     * @param ent Entitlement just created.
     */
    public void init(Entitlement ent) {
        this.ent = ent;
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
        
        Product p = prodAdapter.getProductByLabel(productLabel);
        if (p == null) {
            throw new RuleExecutionException("No such product: " + productLabel);
        }
        
        EntitlementPool consumerSpecificPool = new EntitlementPool(c.getOwner(), 
                p.getOID(), quantity, ent.getPool().getStartDate(), ent.getPool().getEndDate());
        consumerSpecificPool.setConsumer(c);
        consumerSpecificPool.setSourceEntitlement(ent);
        epCurator.create(consumerSpecificPool);
    }
    
}
