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

import org.fedoraproject.candlepin.model.EntitlementPool;
import org.fedoraproject.candlepin.policy.ValidationResult;
import org.fedoraproject.candlepin.policy.java.ReadOnlyEntitlementPool;

public class PreEntHelper {
    
    private Boolean grantFreeEntitlement = Boolean.FALSE;
    private ValidationResult result;
    
    public PreEntHelper() {
        result = new ValidationResult();
    }
    
    public void addError(String resourceKey) {
        result.addError(resourceKey);
    }
    
    public void addWarning(String resourceKey) {
        result.addWarning(resourceKey);
    }
    
    public void grantFreeEntitlement() {
        grantFreeEntitlement = Boolean.TRUE;
    }
    
    public Boolean getGrantFreeEntitlement() {
        return grantFreeEntitlement;
    }
    
    public ValidationResult getResult() {
        return result;
    }
    
    public void checkQuantity(ReadOnlyEntitlementPool entPool) {
        if (!entPool.entitlementsAvailable()) {
            result.addError("rulefailed.no.entitlements.available");
        }
    }

}
