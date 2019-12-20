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
package org.candlepin.policy.activationkey;

import org.candlepin.common.config.PropertyConverter;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.Pool;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.activationkeys.ActivationKeyPool;
import org.candlepin.policy.RulesValidationError;
import org.candlepin.policy.ValidationResult;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;


/**
 * ActivationKeyRules
 *
 * Enforces rules similar to EntitlementRules.java for
 * attaching pools to activation keys
 */
public class ActivationKeyRules {

    /**
     * Keys that represent the various errors that can be produced by the ActivationKeyRules.
     * Error messages may use the following variable(s):
     * {0} - pool id
     */
    public enum ErrorKeys implements RulesValidationError {
        ALREADY_EXISTS("Multi-entitlement not supported for pool \"{0}\""),
        INVALID_QUANTITY("The quantity must be greater than 0"),
        INVALID_NON_MULTIENT_QUANTITY("Error: Only pools with multi-entitlement product" +
            " subscriptions can be added to the activation key with a quantity greater than one."),
        CANNOT_USE_PERSON_POOLS("Cannot add pools that are restricted to unit type \"person\" to" +
            " activation keys.");

        private final String errmsg;

        /**
         * Key constructor that accepts the error's translatable message.
         * @param errmsg The translatable message of the error that this enum represents.
         */
        ErrorKeys(String errmsg) {
            this.errmsg = errmsg;
        }

        @Override
        public String buildErrorMessage(I18n i18n, Object... args) {
            return i18n.tr(this.errmsg, args);
        }
    }

    private static Logger log = LoggerFactory.getLogger(ActivationKeyRules.class);

    private I18n i18n;

    @Inject
    public ActivationKeyRules(I18n i18n) {
        this.i18n = i18n;
    }

    /**
     * Checks that the specified pool can be attached to the specified activation key. A result object
     * is returned which contains errors/warnings, or is empty if validation did not find any issues.
     *
     * @param key The activation key to attach the pool to
     *
     * @param pool The pool to be attached to the activation key
     *
     * @param quantity The quantity of the pool the key will consume
     *
     * @return a result object which contains errors or warnings produced by the validation process.
     *  In case the validation was successful, it will be empty.
     */
    public ValidationResult runPoolValidationForActivationKey(ActivationKey key, Pool pool, Long quantity) {
        ValidationResult result = new ValidationResult();
        if (quantity != null && quantity < 1) {
            result.addError(ErrorKeys.INVALID_QUANTITY);
        }

        if (!PropertyConverter.toBoolean(pool.getMergedProductAttribute(Pool.Attributes.MULTI_ENTITLEMENT))) {
            // If the pool isn't multi-entitlable, we can only accept null quantity and 1
            if (quantity != null && quantity > 1) {
                result.addError(ErrorKeys.INVALID_NON_MULTIENT_QUANTITY);
            }

            // Don't allow non-multi-ent pools to be attached to an activation key more than once
            for (ActivationKeyPool akPool : key.getPools()) {
                if (pool.getId().equals(akPool.getPool().getId())) {
                    result.addError(ErrorKeys.ALREADY_EXISTS);
                    break;
                }
            }
        }

        // Do not allow pools that require a "person" type consumer to be added to an activation key.
        if (ConsumerType.ConsumerTypeEnum.PERSON.getLabel().equalsIgnoreCase(
            pool.getMergedAttribute(Pool.Attributes.REQUIRES_CONSUMER_TYPE))) {

            result.addError(ErrorKeys.CANNOT_USE_PERSON_POOLS);
        }

        return result;
    }

    /**
     * Checks that the specified pool can be attached to the specified activation key. A String
     * is returned which contains the first validation error that was found, or null if validation did
     * not find any issues.
     *
     * @param key The activation key to attach the pool to
     *
     * @param pool The pool to be attached to the activation key
     *
     * @param quantity The quantity of the pool the key will consume
     *
     * @return a String which contains the first validation error that was found, or null if
     *  validation did not find any issues
     */
    public String validatePoolForActivationKey(ActivationKey key, Pool pool, Long quantity) {
        ValidationResult validation = runPoolValidationForActivationKey(key, pool, quantity);
        if (!validation.getErrors().isEmpty()) {
            // Use the first error
            return validation.getErrors().get(0).buildErrorMessage(i18n, pool);
        }
        return null;
    }
}
