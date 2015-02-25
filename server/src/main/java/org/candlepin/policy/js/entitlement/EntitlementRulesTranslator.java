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
import org.candlepin.policy.ValidationError;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

/**
 * Translates error keys returned from entitlement rules into translated error messages.
 */
public class EntitlementRulesTranslator {

    private I18n i18n;
    private Logger log;

    @Inject
    public EntitlementRulesTranslator(I18n i18n) {
        this.i18n = i18n;
        log = LoggerFactory.getLogger(EntitlementRulesTranslator.class);
    }

    public String poolErrorToMessage(Pool pool, ValidationError error) {
        String errorKey = error.getResourceKey();
        String msg = "";
        if (errorKey.equals("rulefailed.consumer.already.has.product")) {
            msg = i18n.tr(
                "This unit has already had the subscription matching " +
                "pool ID ''{0}'' attached.", pool.getId());
        }
        else if (errorKey.equals("rulefailed.no.entitlements.available")) {
            msg = i18n.tr(
                "No subscriptions are available from the pool with " +
                "ID ''{0}''.", pool.getId());
        }
        else if (errorKey.equals("rulefailed.consumer.type.mismatch")) {
            msg = i18n.tr(
                "Units of this type are not allowed to attach the pool " +
                "with ID ''{0}''.", pool.getId());
        }
        else if (errorKey.equals("rulefailed.pool.does.not.support.multi-entitlement")) {
            msg = i18n.tr("Multi-entitlement not supported for pool with ID ''{0}''.",
                pool.getId());
        }
        else if (errorKey.equals("virt.guest.host.does.not.match.pool.owner")) {
            msg = i18n.tr("Guest''s host does not match owner of pool: ''{0}''.",
                pool.getId());
        }
        else if (errorKey.equals("pool.not.available.to.manifest.consumers")) {
            msg = i18n.tr("Pool not available to subscription management " +
                    "applications.");
        }
        else if (errorKey.equals("rulefailed.virt.only")) {
            msg = i18n.tr("Pool is restricted to virtual guests: ''{0}''.",
                pool.getId());
        }
        else if (errorKey.equals("rulefailed.physical.only")) {
            msg = i18n.tr("Pool is restricted to physical systems: ''{0}''.",
                pool.getId());
        }
        else if (errorKey.equals("rulefailed.quantity.mismatch")) {
            String multip = null;
            if (pool.hasProductAttribute("instance_multiplier")) {
                multip = pool.getProductAttribute("instance_multiplier").getValue();
            }
            msg = i18n.tr(
                "Subscription ''{0}'' must be attached using a quantity" +
                " evenly divisible by {1}",
                pool.getProductName(), multip);
        }
        else if (errorKey.equals("rulefailed.instance.unsupported.by.consumer")) {
            msg = i18n.tr("Unit does not support instance based calculation " +
                "required by pool ''{0}''", pool.getId());
        }
        else if (errorKey.equals("rulefailed.cores.unsupported.by.consumer")) {
            msg = i18n.tr("Unit does not support core calculation " +
                "required by pool ''{0}''", pool.getId());
        }
        else if (errorKey.equals("rulefailed.ram.unsupported.by.consumer")) {
            msg = i18n.tr("Unit does not support RAM calculation " +
                "required by pool ''{0}''", pool.getId());
        }
        else if (errorKey.equals("rulefailed.derivedproduct.unsupported.by.consumer")) {
            msg = i18n.tr("Unit does not support derived products data " +
                "required by pool ''{0}''", pool.getId());
        }
        else if (errorKey.equals("virt.guest.cannot.use.unmapped.guest.pool.has.host")) {
            msg = i18n.tr("Pool is restricted to unmapped virtual guests: ''{0}''",
                pool.getId());
        }
        else if (errorKey.equals("virt.guest.cannot.use.unmapped.guest.pool.not.new")) {
            msg = i18n.tr("Pool is restricted to virtual guests in their first day of " +
                "existence: ''{0}''", pool.getId());
        }
        else {
            msg = i18n.tr("Unable to attach pool with ID ''{0}''.: {1}.",
                pool.getId().toString(), errorKey);
        }
        return msg;
    }

    public String productErrorToMessage(String productId, ValidationError error) {
        String errorKey = error.getResourceKey();
        String msg;
        if (errorKey.equals("rulefailed.consumer.already.has.product")) {
            msg = i18n.tr("This system already has a subscription for " +
                    "the product ''{0}'' attached.", productId);
        }
        else if (errorKey.equals("rulefailed.no.entitlements.available")) {
            msg = i18n.tr("There are not enough free subscriptions " +
                "available for the product ''{0}''", productId);
        }
        else if (errorKey.equals("rulefailed.consumer.type.mismatch")) {
            msg = i18n.tr("Units of this type are not allowed for " +
                    "the product ''{0}''.", productId);
        }
        else if (errorKey.equals("rulefailed.virt.only")) {
            msg = i18n.tr(
                "Only virtual systems can have subscription ''{0}'' attached.",
                productId);
        }
        else {
            msg = i18n.tr(
                "Unable to attach subscription for the product ''{0}'': {1}.",
                productId, errorKey);
        }
        return msg;
    }

    public String entitlementErrorToMessage(Entitlement ent, ValidationError error) {
        String errorKey = error.getResourceKey();
        String msg = "";
        if (errorKey.equals("rulefailed.no.entitlements.available")) {
            msg = i18n.tr(
                "Insufficient pool quantity available for adjustment to entitlement " +
                "''{0}''.",
                ent.getId());
        }
        else if (errorKey.equals("rulefailed.pool.does.not.support.multi-entitlement")) {
            msg = i18n.tr("Multi-entitlement not supported for pool connected with " +
                          "entitlement ''{0}''.",
                ent.getId());
        }
        else if (errorKey.equals("rulefailed.consumer.already.has.product")) {
            msg = i18n.tr("Multi-entitlement not supported for pool connected with " +
                          "entitlement ''{0}''.",
                ent.getId());
        }
        else {
            msg = i18n.tr("Unable to adjust quantity for the entitlement with " +
                "id ''{0}'': {1}", ent.getId(), errorKey);
        }
        return msg;
    }
}
