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

import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.model.Product;
import org.candlepin.policy.ValidationError;

import com.google.inject.Inject;

import org.springframework.stereotype.Component;
import org.xnap.commons.i18n.I18n;

import java.util.HashMap;
import java.util.Map;



/**
 * Translates error keys returned from entitlement rules into translated error messages.
 */
@Component
public class EntitlementRulesTranslator {

    // TODO:
    // Add documentation for the intended circumstances for these error keys

    /**
     * Common/known error keys for pool attachment failures
     */
    public static final class PoolErrorKeys {
        public static final String ALREADY_ATTACHED =
            "rulefailed.consumer.already.has.product";

        public static final String NO_ENTITLEMENTS_AVAILABLE =
            "rulefailed.no.entitlements.available";

        public static final String CONSUMER_TYPE_MISMATCH =
            "rulefailed.consumer.type.mismatch";

        public static final String MULTI_ENTITLEMENT_UNSUPPORTED =
            "rulefailed.pool.does.not.support.multi-entitlement";

        public static final String VIRT_HOST_MISMATCH =
            "virt.guest.host.does.not.match.pool.owner";

        public static final String CONSUMER_MISMATCH =
            "consumer.does.not.match.pool.consumer.requirement";

        public static final String RESTRICTED_POOL =
            "pool.not.available.to.manifest.consumers";

        public static final String VIRT_ONLY =
            "rulefailed.virt.only";

        public static final String PHYSICAL_ONLY =
            "rulefailed.physical.only";

        public static final String QUANTITY_MISMATCH =
            "rulefailed.quantity.mismatch";

        public static final String INSTANCE_UNSUPPORTED_BY_CONSUMER =
            "rulefailed.instance.unsupported.by.consumer";

        public static final String BAND_UNSUPPORTED_BY_CONSUMER =
            "rulefailed.band.unsupported.by.consumer";

        public static final String CORES_UNSUPPORTED_BY_CONSUMER =
            "rulefailed.cores.unsupported.by.consumer";

        public static final String RAM_UNSUPPORTED_BY_CONSUMER =
            "rulefailed.ram.unsupported.by.consumer";

        public static final String DERIVED_PRODUCT_DATA_UNSUPPORTED =
            "rulefailed.derivedproduct.unsupported.by.consumer";

        public static final String UNMAPPED_GUEST_RESTRICTED =
            "virt.guest.cannot.use.unmapped.guest.pool.has.host";

        public static final String VIRTUAL_GUEST_RESTRICTED =
            "virt.guest.cannot.use.unmapped.guest.pool.not.new";

        public static final String TEMPORARY_FUTURE_POOL =
            "virt.guest.cannot.bind.future.unmapped.guest.pool";
    }

    /**
     * Common/known error keys for product attachment failures
     */
    public static final class ProductErrorKeys {
        public static final String ALREADY_ATTACHED =
            "rulefailed.consumer.already.has.product";

        public static final String NO_ENTITLEMENTS_AVAILABLE =
            "rulefailed.no.entitlements.available";

        public static final String CONSUMER_TYPE_MISMATCH =
            "rulefailed.consumer.type.mismatch";

        public static final String VIRT_ONLY =
            "rulefailed.virt.only";

    }

    /**
     * Common/known error keys for entitlement attachment failures
     */
    public static final class EntitlementErrorKeys {
        public static final String INSUFFICIENT_QUANTITY =
            "rulefailed.no.entitlements.available";

        public static final String MULTI_ENTITLEMENT_UNSUPPORTED =
            "rulefailed.pool.does.not.support.multi-entitlement";

        public static final String ALREADY_ATTACHED =
            "rulefailed.consumer.already.has.product";
    }

    private static final Map<String, String> POOL_ERROR_MESSAGES;
    private static final String DEFAULT_POOL_ERROR_MESSAGE;

    private static final Map<String, String> PRODUCT_ERROR_MESSAGES;
    private static final String DEFAULT_PRODUCT_ERROR_MESSAGE;

    private static final Map<String, String> ENTITLEMENT_ERROR_MESSAGES;
    private static final String DEFAULT_ENTITLEMENT_ERROR_MESSAGE;

    static {
        // Pool error messages can contain the following variables:
        // {0} - Error key
        // {1} - Pool ID
        // {2} - Pool product (sku) name
        // {3} - Product multiplier
        // {4} - Post (for host-limited pools)
        POOL_ERROR_MESSAGES = new HashMap<>();

        POOL_ERROR_MESSAGES.put(PoolErrorKeys.ALREADY_ATTACHED,
            I18n.marktr("This unit has already had the subscription matching pool ID \"{1}\" attached."));
        POOL_ERROR_MESSAGES.put(PoolErrorKeys.NO_ENTITLEMENTS_AVAILABLE,
            I18n.marktr("No subscriptions are available from the pool with ID \"{1}\"."));
        POOL_ERROR_MESSAGES.put(PoolErrorKeys.CONSUMER_TYPE_MISMATCH,
            I18n.marktr("Units of this type are not allowed to attach the pool with ID \"{1}\"."));
        POOL_ERROR_MESSAGES.put(PoolErrorKeys.MULTI_ENTITLEMENT_UNSUPPORTED,
            I18n.marktr("Multi-entitlement not supported for pool with ID \"{1}\"."));
        POOL_ERROR_MESSAGES.put(PoolErrorKeys.VIRT_HOST_MISMATCH,
            I18n.marktr("Pool \"{1}\" is restricted to guests running on host: \"{4}\"."));
        POOL_ERROR_MESSAGES.put(PoolErrorKeys.CONSUMER_MISMATCH,
            I18n.marktr("Pool \"{1}\" is restricted to a specific consumer."));
        POOL_ERROR_MESSAGES.put(PoolErrorKeys.RESTRICTED_POOL,
            I18n.marktr("Pool not available to subscription management applications."));
        POOL_ERROR_MESSAGES.put(PoolErrorKeys.VIRT_ONLY,
            I18n.marktr("Pool is restricted to virtual guests: \"{1}\"."));
        POOL_ERROR_MESSAGES.put(PoolErrorKeys.PHYSICAL_ONLY,
            I18n.marktr("Pool is restricted to physical systems: \"{1}\"."));
        POOL_ERROR_MESSAGES.put(PoolErrorKeys.QUANTITY_MISMATCH,
            I18n.marktr("Subscription \"{2}\" must be attached using a quantity evenly divisible by {3}"));
        POOL_ERROR_MESSAGES.put(PoolErrorKeys.INSTANCE_UNSUPPORTED_BY_CONSUMER,
            I18n.marktr("Unit does not support instance based calculation required by pool \"{1}\""));
        POOL_ERROR_MESSAGES.put(PoolErrorKeys.BAND_UNSUPPORTED_BY_CONSUMER,
            I18n.marktr("Unit does not support band calculation required by pool \"{1}\""));
        POOL_ERROR_MESSAGES.put(PoolErrorKeys.CORES_UNSUPPORTED_BY_CONSUMER,
            I18n.marktr("Unit does not support core calculation required by pool \"{1}\""));
        POOL_ERROR_MESSAGES.put(PoolErrorKeys.RAM_UNSUPPORTED_BY_CONSUMER,
            I18n.marktr("Unit does not support RAM calculation required by pool \"{1}\""));
        POOL_ERROR_MESSAGES.put(PoolErrorKeys.DERIVED_PRODUCT_DATA_UNSUPPORTED,
            I18n.marktr("Unit does not support derived products data required by pool \"{1}\""));
        POOL_ERROR_MESSAGES.put(PoolErrorKeys.UNMAPPED_GUEST_RESTRICTED,
            I18n.marktr("Pool is restricted to unmapped virtual guests: \"{1}\""));
        POOL_ERROR_MESSAGES.put(PoolErrorKeys.VIRTUAL_GUEST_RESTRICTED,
            I18n.marktr("Pool is restricted to virtual guests in their first day of existence: \"{1}\""));
        POOL_ERROR_MESSAGES.put(PoolErrorKeys.TEMPORARY_FUTURE_POOL,
            I18n.marktr("Pool is restricted when it is temporary and begins in the future: \"{1}\""));
        DEFAULT_POOL_ERROR_MESSAGE =
            I18n.marktr("Unable to attach pool with ID \"{1}\".: {0}.");

        // Product error messages can contain the following variables
        // {0} - Error key
        // {1} - Product ID
        PRODUCT_ERROR_MESSAGES = new HashMap<>();

        PRODUCT_ERROR_MESSAGES.put(ProductErrorKeys.ALREADY_ATTACHED,
            I18n.marktr("This system already has a subscription for the product \"{1}\" attached."));
        PRODUCT_ERROR_MESSAGES.put(ProductErrorKeys.NO_ENTITLEMENTS_AVAILABLE,
            I18n.marktr("There are not enough free subscriptions available for the product \"{1}\""));
        PRODUCT_ERROR_MESSAGES.put(ProductErrorKeys.CONSUMER_TYPE_MISMATCH,
            I18n.marktr("Units of this type are not allowed for the product \"{1}\"."));
        PRODUCT_ERROR_MESSAGES.put(ProductErrorKeys.VIRT_ONLY,
            I18n.marktr("Only virtual systems can have subscription \"{1}\" attached."));
        DEFAULT_PRODUCT_ERROR_MESSAGE =
            I18n.marktr("Unable to attach subscription for the product \"{1}\": {0}.");

        // Entitlement error messages can contain the following variables
        // {0} - Error key
        // {1} - Entitlement ID
        ENTITLEMENT_ERROR_MESSAGES = new HashMap<>();

        ENTITLEMENT_ERROR_MESSAGES.put(EntitlementErrorKeys.INSUFFICIENT_QUANTITY,
            I18n.marktr("Insufficient pool quantity available for adjustment to entitlement \"{1}\"."));
        ENTITLEMENT_ERROR_MESSAGES.put(EntitlementErrorKeys.MULTI_ENTITLEMENT_UNSUPPORTED,
            I18n.marktr("Multi-entitlement not supported for pool connected with entitlement \"{1}\"."));
        ENTITLEMENT_ERROR_MESSAGES.put(EntitlementErrorKeys.ALREADY_ATTACHED,
            I18n.marktr("Multi-entitlement not supported for pool connected with entitlement \"{1}\"."));
        DEFAULT_ENTITLEMENT_ERROR_MESSAGE =
            I18n.marktr("Unable to adjust quantity for the entitlement with id \"{1}\": {0}");
    }

    private static String getPoolErrorMessage(String key) {
        String message = POOL_ERROR_MESSAGES.get(key);
        return message != null ? message : DEFAULT_POOL_ERROR_MESSAGE;
    }

    private static String getProductErrorMessage(String key) {
        String message = PRODUCT_ERROR_MESSAGES.get(key);
        return message != null ? message : DEFAULT_PRODUCT_ERROR_MESSAGE;
    }

    private static String getEntitlementErrorMessage(String key) {
        String message = ENTITLEMENT_ERROR_MESSAGES.get(key);
        return message != null ? message : DEFAULT_ENTITLEMENT_ERROR_MESSAGE;
    }


    private I18n i18n;

    @Inject
    public EntitlementRulesTranslator(I18n i18n) {
        this.i18n = i18n;
    }

    public String poolErrorToMessage(Pool pool, ValidationError error) {
        String host = pool.getAttributeValue(Pool.Attributes.REQUIRES_HOST);
        String multiplier = pool.getProduct() != null ?
            pool.getProduct().getAttributeValue(Product.Attributes.INSTANCE_MULTIPLIER) : null;
        String key = error.getResourceKey();

        return i18n.tr(getPoolErrorMessage(key),
            new Object[] { key, pool.getId(), pool.getProductName(), multiplier, host });
    }

    public String productErrorToMessage(String productId, ValidationError error) {
        String key = error.getResourceKey();

        return i18n.tr(getProductErrorMessage(key), new Object[] { key, productId });
    }

    public String entitlementErrorToMessage(Entitlement entitlement, ValidationError error) {
        String key = error.getResourceKey();

        return i18n.tr(getEntitlementErrorMessage(key), new Object[] { key, entitlement.getId() });
    }
}
