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
package org.fedoraproject.candlepin.enforcer.java;

import java.util.LinkedList;
import java.util.List;

import org.fedoraproject.candlepin.DateSource;
import org.fedoraproject.candlepin.enforcer.Enforcer;
import org.fedoraproject.candlepin.enforcer.ValidationError;
import org.fedoraproject.candlepin.enforcer.ValidationWarning;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.EntitlementPoolCurator;
import org.fedoraproject.candlepin.model.Product;

public class JavaEnforcer implements Enforcer {
    private List<ValidationError> errors = new LinkedList<ValidationError>();
    private List<ValidationWarning> warnings = new LinkedList<ValidationWarning>();

    private DateSource dateSource;
    private EntitlementPoolCurator epCurator;
    
    public static final String VIRTUALIZATION_HOST_PRODUCT = "virtualization_host";
    
    public JavaEnforcer(DateSource dateSource, 
            EntitlementPoolCurator epCurator) {
        this.dateSource = dateSource;
        this.epCurator = epCurator;
    }
    
    @Override
    public List<ValidationError> errors() {
        return errors;
    }

    @Override
    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    @Override
    public List<ValidationWarning> warnings() {
        return warnings;
    }        

    @Override
    public boolean hasWarnings() {
        return !warnings.isEmpty();
    }

    @Override
    public boolean validate(Consumer consumer, 
            EntitlementPool entitlementPool) {

        // TODO: These first checks should probably be pushed to an Enforcer
        // base class, they are implicit and should be done for all
        // implementations.
        if (!epCurator.entitlementsAvailable(entitlementPool)) {
            errors.add(new ValidationError("Not enough entitlements"));
            return false;
        }
                    
        if (entitlementPool.isExpired(dateSource)) {
            errors.add(new ValidationError("Entitlements for " + 
                    entitlementPool.getProduct().getName() + 
                    " expired on: " + entitlementPool.getEndDate()));
            return false;
        }
        
        Product product = entitlementPool.getProduct();
        if (product.getName().equals(VIRTUALIZATION_HOST_PRODUCT)) {
            return validateVirtualizationHost(consumer, entitlementPool);
            
        }
        
        return true;
    }
    
    private boolean validateVirtualizationHost(Consumer consumer, 
            EntitlementPool entitlementPool) {
//        if ((consumer.get))
        return true;
    }
}
