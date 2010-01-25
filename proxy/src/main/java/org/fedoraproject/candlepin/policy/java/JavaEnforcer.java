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

import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.DateSource;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.EntitlementPoolCurator;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductCurator;
import org.fedoraproject.candlepin.model.SpacewalkCertificateCurator;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.ValidationError;
import org.fedoraproject.candlepin.policy.ValidationResult;

import com.google.inject.Inject;

/**
 * A pure java implementation of an Enforcer, with logic for handling the
 * products contained within a Satellite certificate.
 */
public class JavaEnforcer implements Enforcer {

    private DateSource dateSource;
    private static Logger log = Logger.getLogger(JavaEnforcer.class);
    private EntitlementPoolCurator epCurator;
    private ProductCurator prodCurator;

    
    @Inject
    public JavaEnforcer(DateSource dateSource, EntitlementPoolCurator epCurator,
            ProductCurator prodCurator) {
        this.dateSource = dateSource;
        this.epCurator = epCurator;
        this.prodCurator = prodCurator;
    }
    
    @Override
    public ValidationResult validate(Consumer consumer, EntitlementPool entitlementPool) {

        ValidationResult result = new ValidationResult();

        if (!entitlementPool.entitlementsAvailable()) {
        // TODO: These first checks should probably be pushed to an Enforcer
        // base class, they are implicit and should be done for all
        // implementations.
            result.addError(new ValidationError("Not enough entitlements"));
            return result;
        }
                    
        if (entitlementPool.isExpired(dateSource)) {
            result.addError(new ValidationError("Entitlements for " +
                    entitlementPool.getProduct().getName() + 
                    " expired on: " + entitlementPool.getEndDate()));
            return result;
        }
        
        Product product = entitlementPool.getProduct();
        
        if (product.getName().equals(SpacewalkCertificateCurator.PRODUCT_VIRT_HOST)) {
            boolean success = validateVirtualizationHost(consumer, entitlementPool);
            // TODO: need more info here but this code is throwaway anyhow.
            if (!success) {
                result.addError(new ValidationError("Entitlement rules failed."));
            }
        }
        
        return result;
    }
    
    private boolean validateVirtualizationHost(Consumer consumer, 
            EntitlementPool entitlementPool) {
        
        // Imagine this were coming from a YAML file:
        if (
                // only physical system can get this product:
                (consumer.getType().getLabel().equals("system")) &&
                
                // host should have no guests currently:
                (Integer.parseInt(consumer.getFact("total_guests")) == 0))
        {
            return true;
        }
        
        return false;
    }

    public void runPostEntitlementActions(Entitlement ent) {
        Product prod = ent.getProduct();

        Product virtGuestProduct = prodCurator.lookupByLabel(
                SpacewalkCertificateCurator.PRODUCT_VIRT_GUEST);

//        // Virtualization Host
//        if (prod.getLabel().equals(SpacewalkCertificateCurator.PRODUCT_VIRT_HOST)) {
//            new CreateConsumerPoolAction(epCurator, ent, virtGuestProduct,
//                    new Long(5)).run();
//        }
//
//        // Virtualization Host Platform
//        else if (prod.getLabel().equals(
//                SpacewalkCertificateCurator.PRODUCT_VIRT_HOST_PLATFORM)) {
//            new CreateConsumerPoolAction(epCurator, ent, virtGuestProduct,
//                    new Long(-1)).run();
//        }
    }
}
