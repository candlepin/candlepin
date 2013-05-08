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
package org.candlepin.policy.js.compliance;

import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsonJsContext;
import org.candlepin.policy.js.RuleExecutionException;
import org.candlepin.policy.js.RulesObjectMapper;

import com.google.inject.Inject;

import org.apache.log4j.Logger;

import java.util.Date;
import java.util.List;

/**
 * Compliance
 *
 * A class used to check consumer compliance status.
 */
public class ComplianceRules {

    private EntitlementCurator entCurator;
    private JsRunner jsRules;
    private RulesObjectMapper mapper;
    private static Logger log = Logger.getLogger(ComplianceRules.class);
    private StatusReasonMessageGenerator generator;

    @Inject
    public ComplianceRules(JsRunner jsRules, EntitlementCurator entCurator,
        StatusReasonMessageGenerator generator) {
        this.entCurator = entCurator;
        this.jsRules = jsRules;
        this.generator = generator;

        mapper = RulesObjectMapper.instance();
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

        List<Entitlement> ents = entCurator.listByConsumer(c);

        JsonJsContext args = new JsonJsContext(mapper);
        args.put("consumer", c);
        args.put("entitlements", ents);
        args.put("ondate", date);
        args.put("log", log, false);

        // Convert the JSON returned into a ComplianceStatus object:
        String json = jsRules.runJsFunction(String.class, "get_status", args);
        try {
            ComplianceStatus result = mapper.toObject(json, ComplianceStatus.class);
            for (ComplianceReason reason : result.getReasons()) {
                generator.setMessage(c, reason);
            }
            return result;
        }
        catch (Exception e) {
            throw new RuleExecutionException(e);
        }
    }

    public boolean isStackCompliant(Consumer consumer, String stackId,
        List<Entitlement> entsToConsider) {
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("stack_id", stackId);
        args.put("consumer", consumer);
        args.put("entitlements", entsToConsider);
        args.put("log", log, false);
        return jsRules.runJsFunction(Boolean.class, "is_stack_compliant", args);
    }

    public boolean isEntitlementCompliant(Consumer consumer, Entitlement ent) {
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("consumer", consumer);
        args.put("entitlement", ent);
        args.put("log", log, false);
        return jsRules.runJsFunction(Boolean.class, "is_ent_compliant", args);
    }
}
