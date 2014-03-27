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
package org.candlepin.policy.js.activationkey;

import org.candlepin.exceptions.BadRequestException;
import org.candlepin.model.Pool;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsonJsContext;
import org.candlepin.policy.js.RulesObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xnap.commons.i18n.I18n;

import com.google.inject.Inject;

/**
 * ActivationKeyRules
 *
 * Enforces rules similar to EntitlementRules.java for
 * attaching pools to activation keys
 */
public class ActivationKeyRules {

    private static Logger log = LoggerFactory.getLogger(ActivationKeyRules.class);

    private JsRunner jsRules;
    private RulesObjectMapper mapper;
    private I18n i18n;

    @Inject
    public ActivationKeyRules(JsRunner jsRules, I18n i18n) {
        this.jsRules = jsRules;
        this.i18n = i18n;

        mapper = RulesObjectMapper.instance();
        jsRules.init("activation_key_name_space");
    }

    public ValidationResult runPreActKey(ActivationKey key, Pool pool, Long quantity) {
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("key", key);
        args.put("pool", pool);
        args.put("quantity", quantity);
        args.put("log", log, false);

        String json = jsRules.invokeRule("validate_pool", args);
        ValidationResult result = mapper.toObject(json, ValidationResult.class);
        return result;
    }

    public void validatePoolForActKey(ActivationKey key, Pool pool, Long quantity) {
        ValidationResult validation = runPreActKey(key, pool, quantity);
        if (!validation.getErrors().isEmpty()) {
            // Use the first error
            handleActkeyValidationError(
                validation.getErrors().get(0).getResourceKey(), pool);
        }
    }

    private void handleActkeyValidationError(String error, Pool pool) {
        String msg;
        if (error.equals("rulefailed.actkey.single.consumertype")) {
            msg = i18n.tr("Activation keys can only use consumer type restricted " +
                "pools for a single consumer type.");
        }
        else if (error.equals("rulefailed.actkey.cannot.use.person.pools")) {
            msg = i18n.tr("Cannot add pools that are " +
                "restricted to unit type 'person' to activation keys.");
        }
        else if (error.equals("rulefailed.host.restriction.physical.only")) {
            msg = i18n.tr("Cannot use pools with host restriction with physical" +
                " only pools on a single activation key");
        }
        else if (error.equals("rulefailed.multiple.host.restrictions")) {
            msg = i18n.tr("Activation keys can only use host restricted pools from " +
                "a single host.");
        }
        else if (error.equals("rulefailed.already.exists")) {
            msg = i18n.tr("Multi-entitlement not supported for pool ''{0}''", pool.getId());
        }
        else if (error.equals("rulefailed.invalid.nonmultient.quantity")) {
            msg = i18n.tr("Error: Only pools with multi-entitlement product" +
                " subscriptions can be added to the activation key with" +
                " a quantity greater than one.");
        }
        else if (error.equals("rulefailed.insufficient.quantity")) {
            msg = i18n.tr("The quantity must not be greater than the total " +
                "allowed for the pool");
        }
        else if (error.equals("rulefailed.invalid.quantity")) {
            msg = i18n.tr("The quantity must be greater than 0");
        }
        else if (error.equals("rulefailed.invalid.quantity.instancebased.physical")) {
            String multip = null;
            if (pool.hasMergedAttribute("instance_multiplier")) {
                multip = pool.getMergedAttribute("instance_multiplier").getValue();
            }
            msg = i18n.tr("Activation key with for physical systems can only use " +
                "quantities of pool ''{0}'' evenly divisible by {1}", pool.getId(), multip);
        }
        else if (error.equals("rulefailed.virtonly.on.physical.key")) {
            msg = i18n.tr("Cannot add virtual pool ''{0}'' to activation key" +
                " for physical systems.", pool.getId());
        }
        else if (error.equals("rulefailed.physicalonly.on.virt.key")) {
            msg = i18n.tr("Cannot add physical pool ''{0}'' to activation key" +
                " for virtual systems.", pool.getId());
        }
        else {
            msg = error;
        }
        throw new BadRequestException(msg);
    }
}
