/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.candlepin.policy.js.compliance;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.log4j.Logger;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.policy.js.JsRules;
import org.candlepin.policy.js.RuleExecutionException;
import org.mozilla.javascript.RhinoException;

import com.google.inject.Inject;

/**
 * Compliance
 *
 * A class used to check consumer compliance status.
 */
public class ComplianceRules {

    private EntitlementCurator entCurator;
    private JsRules jsRules;
    private static Logger log = Logger.getLogger(ComplianceRules.class);

    @Inject
    public ComplianceRules(JsRules jsRules, EntitlementCurator entCurator) {
        this.entCurator = entCurator;
        this.jsRules = jsRules;

        jsRules.init("compliance_name_space");
    }

    /**
     * Check compliance status for a consumer on a specific date.
     *
     * @param c Consumer to check.
     * @param date Date to check compliance status for.
     * @return Compliance status.
     */
    public ComplianceStatus getStatus(Consumer c, Date date) {

        List<Entitlement> ents = entCurator.listByConsumerAndDate(c, date);

        Map<String, Object> args = new HashMap<String, Object>();
        args.put("consumer", c);
        args.put("entitlements", ents);
        args.put("ondate", date);
        args.put("helper", new ComplianceRulesHelper(entCurator));
        args.put("log", log);
        return runJsFunction(ComplianceStatus.class, "get_status", args);
    }

    public boolean isStackCompliant(Consumer consumer, String stackId,
        List<Entitlement> entsToConsider) {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("stack_id", stackId);
        args.put("consumer", consumer);
        args.put("entitlements", entsToConsider);
        args.put("log", log);
        return runJsFunction(Boolean.class, "is_stack_compliant", args);
    }

    public boolean isEntitlementCompliant(Consumer consumer, Entitlement ent) {
        Map<String, Object> args = new HashMap<String, Object>();
        args.put("consumer", consumer);
        args.put("ent", ent);
        args.put("log", log);
        return runJsFunction(Boolean.class, "is_ent_compliant", args);
    }

    private <T extends Object> T runJsFunction(Class<T> clazz, String function,
        Map<String, Object> args) {
        T returner = null;
        try {
            returner = jsRules.invokeMethod(function, args);
        }
        catch (NoSuchMethodException e) {
            log.warn("No compliance javascript method found: " + function);
        }
        catch (RhinoException e) {
            throw new RuleExecutionException(e);
        }
        return returner;
    }

}
