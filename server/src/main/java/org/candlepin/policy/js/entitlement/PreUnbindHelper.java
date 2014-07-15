/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.policy.js.entitlement;

import org.candlepin.model.ConsumerCurator;
import org.candlepin.policy.ValidationResult;

/**
 * Helper class for the pre-entitlement functions in our Javascript rules.
 *
 * Object is used as a holder for utility methods useful to all rules files, as well as
 * a mechanism for the rules to return a small amount of state.
 */
public class PreUnbindHelper {

    private ValidationResult result;
    private ConsumerCurator consumerCurator;

    public PreUnbindHelper(ConsumerCurator consumerCurator) {
        this.consumerCurator = consumerCurator;
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
     * Return the result of the validation
     * @return the result of the validation
     */
    public ValidationResult getResult() {
        return result;
    }
}
