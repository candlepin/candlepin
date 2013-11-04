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
package org.candlepin.policy.js.quantity;

import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Pool;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsonJsContext;
import org.candlepin.policy.js.RulesObjectMapper;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.HashSet;
import java.util.Set;

/**
 * QuantityRules
 */
public class QuantityRules {

    private JsRunner jsRules;
    private RulesObjectMapper mapper;
    private static Logger log = LoggerFactory.getLogger(QuantityRules.class);

    @Inject
    public QuantityRules(JsRunner jsRules) {
        this.jsRules = jsRules;

        mapper = RulesObjectMapper.instance();
        jsRules.init("quantity_name_space");
    }

    public SuggestedQuantity getSuggestedQuantity(Pool p, Consumer c, Date date) {
        JsonJsContext args = new JsonJsContext(mapper);

        Set<Entitlement> validEntitlements = new HashSet<Entitlement>();
        for (Entitlement e : c.getEntitlements()) {
            if (e.isValidOnDate(date)) {
                validEntitlements.add(e);
            }
        }

        args.put("pool", p);
        args.put("consumer", c);
        args.put("validEntitlements", validEntitlements);
        args.put("log", log, false);

        String json = jsRules.runJsFunction(String.class, "get_suggested_quantity", args);
        SuggestedQuantity dto = mapper.toObject(json, SuggestedQuantity.class);
        return dto;
    }
}
