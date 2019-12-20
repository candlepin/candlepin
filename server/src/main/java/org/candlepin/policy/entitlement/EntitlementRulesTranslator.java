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
package org.candlepin.policy.entitlement;

import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.policy.RulesValidationError;
import org.candlepin.policy.RulesValidationWarning;

import com.google.inject.Inject;

import org.xnap.commons.i18n.I18n;


/**
 * Translates error keys returned from entitlement rules into translated error messages.
 */
public class EntitlementRulesTranslator {

    // TODO:
    // Add documentation for the intended circumstances for these error keys

    /**
     * Keys that represent the various warnings that can be produced by the EntitlementRules.
     * Warning messages may use the following variable(s):
     * {0} - pool id
     */
    public enum WarningKeys implements RulesValidationWarning {
        ARCHITECTURE_MISMATCH(
            I18n.marktr("The entitlement's product architecture does not match with the consumer's.")),
        DERIVED_PRODUCT_UNSUPPORTED_BY_CONSUMER(
            I18n.marktr("Unit does not support derived products data required by pool \"{0}\"")),
        VCPU_NUMBER_UNSUPPORTED(
            I18n.marktr("Pool \"{0}\" does not cover the consumer's vcpus.")),
        SOCKET_NUMBER_UNSUPPORTED(
            I18n.marktr("Pool \"{0}\" does not cover the consumer's sockets.")),
        CORE_NUMBER_UNSUPPORTED(
            I18n.marktr("Pool \"{0}\" does not cover the consumer's cores.")),
        RAM_NUMBER_UNSUPPORTED(
            I18n.marktr("Pool \"{0}\" does not cover the consumer's ram.")),
        STORAGE_BAND_NUMBER_UNSUPPORTED(
            I18n.marktr("Pool \"{0}\" does not cover the consumer's storage band usage.")),
        CORES_UNSUPPORTED_BY_CONSUMER(
            I18n.marktr("Unit does not support core calculation required by pool \"{0}\"")),
        RAM_UNSUPPORTED_BY_CONSUMER(
            I18n.marktr("Unit does not support RAM calculation required by pool \"{0}\"")),
        STORAGE_BAND_UNSUPPORTED_BY_CONSUMER(
            I18n.marktr("Unit does not support storage band calculation required by pool \"{0}\"")),
        VIRT_ONLY(
            I18n.marktr("Pool is restricted to virtual guests: \"{0}\".")),
        PHYSICAL_ONLY(
            I18n.marktr("Pool is restricted to physical systems: \"{0}\".")),
        INSTANCE_UNSUPPORTED_BY_CONSUMER(
            I18n.marktr("Unit does not support instance based calculation required by pool \"{0}\""));

        private final String warnmsg;

        /**
         * Key constructor that accepts the warning's translatable message.
         * @param warnmsg The translatable message of the warning that this enum represents.
         */
        WarningKeys(String warnmsg) {
            this.warnmsg = warnmsg;
        }

        @Override
        public String buildWarningMessage(I18n i18n, Object... args) {
            return i18n.tr(this.warnmsg, args);
        }
    }

    /**
     * Keys that represent the various errors that can be produced by the EntitlementRules.
     * Each error key might map to one, two or three error messages (depending on the context, which can be
     * the validation of a pool, entitlement, or product).
     *
     * Error messages may use the following variable(s):
     * {0} - Error message type
     * {1} - Pool id OR Entitlement id OR Product id
     * {2} - Pool restricted username
     * {3} - Host consumer uuid
     * {4} - Pool product (sku) name
     * {5} - Product multiplier
     * {6} - Pool's product id
     * {7} - Pool end date
     */
    public enum ErrorKeys implements RulesValidationError {
        DERIVED_PRODUCT_UNSUPPORTED_BY_CONSUMER(
            I18n.marktr("Unit does not support derived products data required by pool \"{1}\"")),
        ALREADY_ATTACHED(
            I18n.marktr("This unit has already had the subscription matching pool ID \"{1}\" attached."),
            I18n.marktr("Multi-entitlement not supported for pool connected with entitlement \"{1}\"."),
            I18n.marktr("This system already has a subscription for the product \"{1}\" attached.")),
        MULTI_ENTITLEMENT_UNSUPPORTED(
            I18n.marktr("Multi-entitlement not supported for pool with ID \"{1}\"."),
            I18n.marktr("Multi-entitlement not supported for pool connected with entitlement \"{1}\".")),
        CONSUMER_TYPE_MISMATCH(
            I18n.marktr("Units of this type are not allowed to attach the pool with ID \"{1}\"."),
            DEFAULT_ENTITLEMENT_ERROR_MESSAGE,
            I18n.marktr("Units of this type are not allowed for the product \"{1}\".")),
        POOL_NOT_AVAILABLE_TO_USER(
            I18n.marktr("Pool is only available to user \"{2}\".")),
        CORES_UNSUPPORTED_BY_CONSUMER(
            I18n.marktr("Unit does not support core calculation required by pool \"{1}\"")),
        RAM_UNSUPPORTED_BY_CONSUMER(
            I18n.marktr("Unit does not support RAM calculation required by pool \"{1}\"")),
        STORAGE_BAND_UNSUPPORTED_BY_CONSUMER(
            I18n.marktr("Unit does not support band calculation required by pool \"{1}\"")),
        RESTRICTED_POOL(
            I18n.marktr("Pool not available to subscription management applications.")),
        VIRT_ONLY(
            I18n.marktr("Pool is restricted to virtual guests: \"{1}\"."),
            DEFAULT_ENTITLEMENT_ERROR_MESSAGE,
           I18n.marktr("Only virtual systems can have subscription \"{1}\" attached.")),
        PHYSICAL_ONLY(
            I18n.marktr("Pool is restricted to physical systems: \"{1}\".")),
        UNMAPPED_GUEST_RESTRICTED(
            I18n.marktr("Pool is restricted to unmapped virtual guests: \"{1}\"")),
        VIRTUAL_GUEST_RESTRICTED(
            I18n.marktr("Pool is restricted to virtual guests in their first day of existence: \"{1}\"")),
        TEMPORARY_FUTURE_POOL(
            I18n.marktr("Pool is restricted when it is temporary and begins in the future: \"{1}\"")),
        CONSUMER_MISMATCH(
            I18n.marktr("Pool \"{1}\" is restricted to a specific consumer.")),
        VIRT_HOST_MISMATCH(
            I18n.marktr("Pool \"{1}\" is restricted to guests running on host: \"{3}\".")),
        QUANTITY_MISMATCH(
            I18n.marktr("Subscription \"{4}\" must be attached using a quantity evenly divisible by {5}")),
        INSTANCE_UNSUPPORTED_BY_CONSUMER(
            I18n.marktr("Unit does not support instance based calculation required by pool \"{1}\"")),
        NO_ENTITLEMENTS_AVAILABLE(
            I18n.marktr("No subscriptions are available from the pool with ID \"{1}\"."),
            I18n.marktr("Insufficient pool quantity available for adjustment to entitlement \"{1}\"."),
            I18n.marktr("There are not enough free subscriptions available for the product \"{1}\""));

        private final String poolErrorMessage;
        private final String entitlementErrorMessage;
        private final String productErrorMessage;

        /**
         * Validation error key constructor that accepts the error's translatable message.
         * @param poolErrorMessage The translatable message of the error that this enum represents, within
         * the context of validating against a pool.
         */
        ErrorKeys(String poolErrorMessage) {
            this.poolErrorMessage = poolErrorMessage;
            this.entitlementErrorMessage = DEFAULT_ENTITLEMENT_ERROR_MESSAGE;
            this.productErrorMessage = DEFAULT_PRODUCT_ERROR_MESSAGE;
        }

        /**
         * Validation error key constructor that accepts the error's translatable messages.
         * @param poolErrorMessage The translatable message of the error that this enum instance represents,
         * within the context of validating against a pool.
         * @param entitlementErrorMessage The translatable message of the error that this enum instance
         * represents, within the context of validating against an entitlement.
         */
        ErrorKeys(String poolErrorMessage, String entitlementErrorMessage) {
            this.poolErrorMessage = poolErrorMessage;
            this.entitlementErrorMessage = entitlementErrorMessage;
            this.productErrorMessage = DEFAULT_PRODUCT_ERROR_MESSAGE;
        }

        /**
         * Validation error key constructor that accepts the error's translatable messages.
         * @param poolErrorMessage The translatable message of the error that this enum instance represents,
         * within the context of validating against a pool.
         * @param entitlementErrorMessage The translatable message of the error that this enum instance
         * represents, within the context of validating against an entitlement.
         * @param productErrorMessage The translatable message of the error that this enum instance
         * represents, within the context of validating against a product.
         */
        ErrorKeys(String poolErrorMessage, String entitlementErrorMessage, String productErrorMessage) {
            this.poolErrorMessage = poolErrorMessage;
            this.entitlementErrorMessage = entitlementErrorMessage;
            this.productErrorMessage =  productErrorMessage;
        }

        @Override
        public String buildErrorMessage(I18n i18n, Object... args) {
            if (args[0] == ErrorMessageTypes.POOL_ERROR) {
                return i18n.tr(this.poolErrorMessage, args);
            }
            else if (args[0] == ErrorMessageTypes.ENTITLEMENT_ERROR) {
                return i18n.tr(this.entitlementErrorMessage, args);
            }
            else if (args[0] == ErrorMessageTypes.PRODUCT_ERROR) {
                return i18n.tr(this.productErrorMessage, args);
            }
            throw new IllegalArgumentException("Error message type is required!");
        }
    }

    /**
     * Indicates the context that the error happened in.
     */
    private enum ErrorMessageTypes {
        POOL_ERROR,
        ENTITLEMENT_ERROR,
        PRODUCT_ERROR
    }

    private static final String DEFAULT_PRODUCT_ERROR_MESSAGE =
        I18n.marktr("Unable to attach subscription for the product \"{1}\": {0}.");

    private static final String DEFAULT_ENTITLEMENT_ERROR_MESSAGE =
        I18n.marktr("Unable to adjust quantity for the entitlement with id \"{1}\": {0}");

    private I18n i18n;

    @Inject
    public EntitlementRulesTranslator(I18n i18n) {
        this.i18n = i18n;
    }

    public String poolErrorToMessage(Pool pool, RulesValidationError error) {
        String host = pool.getAttributeValue(Pool.Attributes.REQUIRES_HOST);
        String multiplier = pool.getProduct() != null ?
            pool.getProduct().getAttributeValue(Product.Attributes.INSTANCE_MULTIPLIER) : null;

        return error.buildErrorMessage(this.i18n, ErrorMessageTypes.POOL_ERROR, pool.getId(),
            pool.getRestrictedToUsername(), host, pool.getProductName(), multiplier, pool.getProductId(),
            pool.getEndDate());
    }

    public String productErrorToMessage(String productId, RulesValidationError error) {
        return error.buildErrorMessage(this.i18n, ErrorMessageTypes.PRODUCT_ERROR, productId);
    }

    public String entitlementErrorToMessage(Entitlement entitlement, RulesValidationError error) {
        return error.buildErrorMessage(this.i18n, ErrorMessageTypes.PRODUCT_ERROR, entitlement.getId());
    }
}
