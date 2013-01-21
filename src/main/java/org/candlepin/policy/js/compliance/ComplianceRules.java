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

import java.util.Date;
import java.util.List;

import org.apache.log4j.Logger;
import org.candlepin.jackson.ExportBeanPropertyFilter;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsonJsContext;
import org.candlepin.policy.js.JsContext;
import org.candlepin.policy.js.RuleExecutionException;
import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.introspect.JacksonAnnotationIntrospector;
import org.codehaus.jackson.map.ser.impl.SimpleFilterProvider;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.mozilla.javascript.RhinoException;

import com.google.inject.Inject;

/**
 * Compliance
 *
 * A class used to check consumer compliance status.
 */
public class ComplianceRules {

    private EntitlementCurator entCurator;
    private JsRunner jsRules;
    private ObjectMapper mapper;
    private static Logger log = Logger.getLogger(ComplianceRules.class);

    @Inject
    public ComplianceRules(JsRunner jsRules, EntitlementCurator entCurator) {
        this.entCurator = entCurator;
        this.jsRules = jsRules;

        mapper = new ObjectMapper();
        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        filterProvider.setDefaultFilter(new ExportBeanPropertyFilter());
        mapper.setFilters(filterProvider);

        AnnotationIntrospector primary = new JacksonAnnotationIntrospector();
        AnnotationIntrospector secondary = new JaxbAnnotationIntrospector();
        AnnotationIntrospector pair = new AnnotationIntrospector.Pair(primary, secondary);
        mapper.setAnnotationIntrospector(pair);

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

        JsonJsContext args = new JsonJsContext();
        args.put("consumer", c);
        args.put("entitlements", ents);
        args.put("ondate", date);
        args.put("helper", new ComplianceRulesHelper(entCurator), false);
        args.put("log", log, false);

        // Convert the JSON returned into a ComplianceStatus object:
        String json = runJsFunction(String.class, "get_status", args);
        log.warn(json);
        try {
            ComplianceStatus status = mapper.readValue(json, ComplianceStatus.class);
            return status;
        }
        catch (Exception e) {
            throw new RuleExecutionException(e);
        }
    }

    public boolean isStackCompliant(Consumer consumer, String stackId,
        List<Entitlement> entsToConsider) {
        JsonJsContext args = new JsonJsContext();
        args.put("stack_id", stackId);
        args.put("consumer", consumer);
        args.put("entitlements", entsToConsider);
        args.put("log", log, false);
        return runJsFunction(Boolean.class, "is_stack_compliant", args);
    }

    public boolean isEntitlementCompliant(Consumer consumer, Entitlement ent) {
        JsonJsContext args = new JsonJsContext();
        args.put("consumer", consumer);
        args.put("ent", ent);
        args.put("log", log, false);
        return runJsFunction(Boolean.class, "is_ent_compliant", args);
    }

    private <T extends Object> T runJsFunction(Class<T> clazz, String function,
        JsContext context) {
        T returner = null;
        try {
            returner = jsRules.invokeMethod(function, context);
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
