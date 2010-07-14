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

import org.fedoraproject.candlepin.policy.ValidationResult;

/**
 * Helper class for the pre-entitlement functions in our Javascript rules.
 * 
 * Object is used as a holder for utility methods useful to all rules files, as well as 
 * a mechanism for the rules to return a small amount of state.
 */
public class PreEntHelper {
    
    private Boolean grantFreeEntitlement = Boolean.FALSE;
    private ValidationResult result;
    private Integer quantityToConsume;
   
    public PreEntHelper(Integer quantityToConsume) {
        this.quantityToConsume = quantityToConsume;
        result = new ValidationResult();
    }
   
    /**
     * Add an error message to the validation results.
     * @param resourceKey key of the error message.
     */
    public void addError(String resourceKey) {
        result.addError(resourceKey);
    }
   
    /**
     * Add a warning message to the validation.
     * @param resourceKey key 
     */
    public void addWarning(String resourceKey) {
        result.addWarning(resourceKey);
    }
    
    /**
     * Called by a rule who wishes to indicate the entitlement is free,
     * and not counted against the owner's overall consumption. This
     * translates into a flag on the Entitlement object itself.
     */
    public void grantFreeEntitlement() {
        grantFreeEntitlement = Boolean.TRUE;
    }
   
    /**
     * Return true if we are to grant free entitlements.
     * @return true if we are to grant free entitlements.
     */
    public Boolean getGrantFreeEntitlement() {
        return grantFreeEntitlement;
    }
   
    /**
     * Return the result of the validation
     * @return the result of the validation
     */
    public ValidationResult getResult() {
        return result;
    }

    /**
     * Verify entitlements are available in the given pool.
     * 
     * WARNING: It is extremely important the author of a rules file makes
     * sure this function is called at appropriate times in pre_global() and
     * normally within all product specific functions. If not, entitlements
     * will be granted with no checking against overall consumption limits,
     * leaving a scenario that will have to be dealt with via compliance
     * checking.
     *  
     * @param entPool read-only entitlement pool to be checked.
     */
    public void checkQuantity(ReadOnlyPool entPool) {
        if (!entPool.entitlementsAvailable(quantityToConsume)) {
            result.addError("rulefailed.no.entitlements.available");
        }
    }

}
