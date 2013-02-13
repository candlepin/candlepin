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

import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.AttributeHelper;
import org.candlepin.policy.js.ReadOnlyPool;

/**
 * Helper class for the pre-entitlement functions in our Javascript rules.
 *
 * Object is used as a holder for utility methods useful to all rules files, as well as
 * a mechanism for the rules to return a small amount of state.
 */
public class PreEntHelper extends AttributeHelper {

    private ValidationResult result;
    private Integer quantityToConsume;
    private ConsumerCurator consumerCurator;

    public PreEntHelper(Integer quantityToConsume, ConsumerCurator consumerCurator) {
        this.quantityToConsume = quantityToConsume;
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

    public Integer getQuantity() {
        return quantityToConsume;
    }

    /**
     * Lookup a host consumer for the given guest ID, if one exists. The host may not
     * be registered to Candlepin.
     *
     * @param guestId Virt guest ID to search for a host for.
     *
     * @return Consumer
     */
    public Consumer getHostConsumer(String guestId) {
        return consumerCurator.getHost(guestId);
    }
}
