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
package org.fedoraproject.candlepin.policy.java;

import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementPoolCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductCurator;
import org.fedoraproject.candlepin.model.SpacewalkCertificateCurator;
import org.fedoraproject.candlepin.policy.PostEntitlementProcessor;
import org.fedoraproject.candlepin.policy.actions.CreateConsumerPoolAction;

import com.google.inject.Inject;

public class JavaPostEntitlementProcessor implements PostEntitlementProcessor {
    
    private EntitlementPoolCurator epCurator;
    private ProductCurator prodCurator;
    
    @Inject
    public JavaPostEntitlementProcessor(EntitlementPoolCurator epCurator, 
            ProductCurator prodCurator) {
        this.epCurator = epCurator;
        this.prodCurator = prodCurator;
    }

    public void run(Entitlement ent) {
        Product prod = ent.getProduct();
        
        Product virtGuestProduct = prodCurator.lookupByLabel(
                SpacewalkCertificateCurator.PRODUCT_VIRT_GUEST);
        if (prod.getLabel().equals(JavaEnforcer.VIRTUALIZATION_HOST_PRODUCT)) {
            new CreateConsumerPoolAction(epCurator, ent, virtGuestProduct,
                    new Long(5)).run();
        }
    }

}
