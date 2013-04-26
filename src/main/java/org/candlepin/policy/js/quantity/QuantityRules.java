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
import org.candlepin.policy.js.AttributeHelper;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsonJsContext;
import org.candlepin.policy.js.RulesObjectMapper;

import com.google.inject.Inject;

import org.apache.log4j.Logger;

import java.util.Date;

/**
 * QuantityRules
 */
public class QuantityRules {

    private JsRunner jsRules;
    private RulesObjectMapper mapper;
    private static Logger log = Logger.getLogger(QuantityRules.class);

    @Inject
    public QuantityRules(JsRunner jsRules) {
        this.jsRules = jsRules;

        mapper = RulesObjectMapper.instance();
        jsRules.init("quantity_name_space");
    }

    public long getSuggestedQuantity(Pool p, Consumer c) {
        AttributeHelper attributeHelper = new AttributeHelper();

        int totalConsumed = 0;
        for (Entitlement e : c.getEntitlements()) {
            if (e.getProductId().equals(p.getProductId()) && isValid(e)) {
                totalConsumed += e.getQuantity();
            }
        }

        JsonJsContext args = new JsonJsContext(mapper);
        args.put("productAttributes", attributeHelper.getFlattenedAttributes(p));
        args.put("consumerFacts", c.getFacts());
        args.put("totalConsumed", totalConsumed);
        args.put("log", log, false);

        // Fun fact: All numbers in javascript (ECMAScript) are double-precision.
        // See http://www.ecma-international.org/ecma-262/5.1/#sec-8.5

        // For some reason Rhino will return an integer sometimes and a double
        // other times.  So let's just deal with Strings to make everything
        // consistent.
        String q = jsRules.runJsFunction(String.class, "get_suggested_quantity", args);
        return Long.valueOf(q);
    }

    private boolean isValid(Entitlement e) {
        Date now = new Date();
        return now.after(e.getCreated()) && now.before(e.getEndDate());
    }
}
