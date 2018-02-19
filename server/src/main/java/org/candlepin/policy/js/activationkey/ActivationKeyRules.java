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

import org.candlepin.common.exceptions.BadRequestException;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.rules.v1.PoolDTO;
import org.candlepin.model.Pool;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.policy.ValidationResult;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsonJsContext;
import org.candlepin.policy.js.RulesObjectMapper;

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

    private static Logger log = LoggerFactory.getLogger(ActivationKeyRules.class);

    private JsRunner jsRules;
    private RulesObjectMapper mapper;
    private I18n i18n;
    private ModelTranslator translator;

    @Inject
    public ActivationKeyRules(JsRunner jsRules, I18n i18n, RulesObjectMapper mapper,
        ModelTranslator translator) {
        this.jsRules = jsRules;
        this.i18n = i18n;

        this.mapper = mapper;
        this.translator = translator;
        jsRules.init("activation_key_name_space");
    }

    public ValidationResult runPreActKey(ActivationKey key, Pool pool, Long quantity) {
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("key", key);
        args.put("pool", this.translator.translate(pool, PoolDTO.class));
        args.put("quantity", quantity);
        args.put("log", log, false);

        String json = jsRules.invokeRule("validate_pool", args);
        return mapper.toObject(json, ValidationResult.class);
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
        if (error.equals("rulefailed.actkey.cannot.use.person.pools")) {
            msg = i18n.tr("Cannot add pools that are " +
                "restricted to unit type ''person'' to activation keys.");
        }
        else if (error.equals("rulefailed.already.exists")) {
            msg = i18n.tr("Multi-entitlement not supported for pool ''{0}''", pool.getId());
        }
        else if (error.equals("rulefailed.invalid.nonmultient.quantity")) {
            msg = i18n.tr("Error: Only pools with multi-entitlement product" +
                " subscriptions can be added to the activation key with" +
                " a quantity greater than one.");
        }
        else if (error.equals("rulefailed.invalid.quantity")) {
            msg = i18n.tr("The quantity must be greater than 0");
        }
        else {
            msg = error;
        }
        throw new BadRequestException(msg);
    }
}
