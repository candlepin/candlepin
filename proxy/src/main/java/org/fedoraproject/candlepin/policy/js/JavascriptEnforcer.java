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


import org.apache.log4j.Logger;
import org.fedoraproject.candlepin.DateSource;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.model.EntitlementPoolCurator;
import org.fedoraproject.candlepin.model.ProductCurator;
import org.fedoraproject.candlepin.policy.Enforcer;
import org.fedoraproject.candlepin.policy.ValidationError;
import org.fedoraproject.candlepin.policy.ValidationResult;

import com.google.inject.Inject;

public class JavascriptEnforcer implements Enforcer {
    
    private static Logger log = Logger.getLogger(JavascriptEnforcer.class);
    private Rules rules;
    private DateSource dateSource;
    private EntitlementPoolCurator epCurator;
    private ProductCurator prodCurator;

    
    @Inject
    public JavascriptEnforcer(DateSource dateSource, EntitlementPoolCurator epCurator,
            ProductCurator prodCurator) {
        this.dateSource = dateSource;
        this.epCurator = epCurator;
        this.prodCurator = prodCurator;

        this.rules = new Rules("/rules/satellite-rules.js");
    }


    @Override
    public ValidationResult validate(Consumer consumer, EntitlementPool entitlementPool) {

        ValidationResult result = rules.validateProduct(consumer, entitlementPool);
        if (!result.isSuccessful()) {
//            throw new
        }

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

        return result;
    }

    @Override
    public void runPostEntitlementActions(Entitlement ent) {
        // TODO Auto-generated method stub

    }

}
