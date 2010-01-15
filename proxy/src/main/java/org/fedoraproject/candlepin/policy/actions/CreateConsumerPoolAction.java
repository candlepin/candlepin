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

import java.util.Date;

import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.EntitlementPoolCurator;
import org.fedoraproject.candlepin.model.Product;

/**
 * Action to create an entitlement pool for a specific consumer and product.
 */
public class CreateConsumerPoolAction implements Action {

    EntitlementPoolCurator epCurator;
    Consumer consumer;
    Product product;
    Long quantity;
    Date startDate;
    Date endDate;
    
    public CreateConsumerPoolAction(EntitlementPoolCurator epCuratorIn, 
            Consumer consumer, Product product, Long quantity, Date startDate, Date endDate) {
        this.epCurator = epCuratorIn;
        this.consumer = consumer;
        this.product = product;
        this.quantity = quantity;
        this.startDate = startDate;
        this.endDate = endDate;
    }
    
    public void run(Entitlement ent) {
        EntitlementPool consumerSpecificPool = new EntitlementPool(consumer.getOwner(), 
                product, quantity, startDate, endDate);
        epCurator.create(consumerSpecificPool);
    }
    
}
