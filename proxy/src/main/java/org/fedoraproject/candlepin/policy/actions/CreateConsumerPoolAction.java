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
package org.fedoraproject.candlepin.policy.actions;

import java.util.List;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.EntitlementPoolCurator;
import org.fedoraproject.candlepin.model.Product;

/**
 * Action to create an entitlement pool for a specific consumer and product.
 */
public class CreateConsumerPoolAction {

    EntitlementPoolCurator epCurator;
    Entitlement ent;
    Long quantity;
    Product product;
    
    public CreateConsumerPoolAction(EntitlementPoolCurator epCurator, 
            Entitlement ent, Product product, Long quantity) {
        this.epCurator = epCurator;
        this.ent = ent;
        this.product = product;
        this.quantity = quantity;
        
    }
    
    public void run() {
        Consumer c = ent.getConsumer();
        EntitlementPool consumerSpecificPool = new EntitlementPool(c.getOwner(), 
                product, quantity, ent.getPool().getStartDate(), ent.getPool().getEndDate());
        consumerSpecificPool.setConsumer(c);
        epCurator.create(consumerSpecificPool);
    }
    
}
