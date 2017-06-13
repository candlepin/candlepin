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

import org.candlepin.audit.EventSink;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerCurator;
import org.candlepin.model.Entitlement;
import org.candlepin.model.EntitlementCurator;
import org.candlepin.policy.js.JsRunner;
import org.candlepin.policy.js.JsonJsContext;
import org.candlepin.policy.js.RuleExecutionException;
import org.candlepin.policy.js.RulesObjectMapper;
import org.candlepin.policy.js.compliance.hash.ComplianceStatusHasher;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;



/**
 * ComplianceRules
 *
 * A class used to check consumer compliance status.
 */
public class ComplianceRules {

    private EntitlementCurator entCurator;
    private JsRunner jsRules;
    private RulesObjectMapper mapper;
    private static Logger log = LoggerFactory.getLogger(ComplianceRules.class);
    private StatusReasonMessageGenerator generator;
    private EventSink eventSink;
    // Use the curator to update consumer entitlement status every time we run compliance (with null date)
    private ConsumerCurator consumerCurator;

    @Inject
    public ComplianceRules(JsRunner jsRules, EntitlementCurator entCurator,
        StatusReasonMessageGenerator generator, EventSink eventSink,
        ConsumerCurator consumerCurator, RulesObjectMapper mapper) {
        this.entCurator = entCurator;
        this.jsRules = jsRules;
        this.generator = generator;
        this.eventSink = eventSink;
        this.consumerCurator = consumerCurator;
        this.mapper = mapper;
        jsRules.init("compliance_name_space");
    }

    /**
     * Check compliance status for a consumer on a specific date.
     * This should NOT calculate compliantUntil.
     *
     * @param c Consumer to check.
     * @return Compliance status.
     */
    public ComplianceStatus getStatus(Consumer c) {
        return getStatus(c, null, false);
    }

    /**
     * Check compliance status for a consumer on a specific date.
     *
     * @param c Consumer to check.
     * @param date Date to check compliance status for.
     * @return Compliance status.
     */
    public ComplianceStatus getStatus(Consumer c, Date date) {
        return getStatus(c, date, true);
    }

    /**
     * Check compliance status for a consumer on a specific date.
     *
     * @param c Consumer to check.
     * @param date Date to check compliance status for.
     * @param calculateCompliantUntil calculate how long the system will remain compliant (expensive)
     * @return Compliance status.
     */
    public ComplianceStatus getStatus(Consumer c, Date date, boolean calculateCompliantUntil) {
        boolean currentCompliance = false;
        if (date == null) {
            currentCompliance = true;
        }
        return getStatus(c, null, date, calculateCompliantUntil, true, false, currentCompliance);
    }

    /**
     * Check compliance status for a consumer on a specific date.
     *
     * @param c Consumer to check.
     * @param date Date to check compliance status for.
     * @param calculateCompliantUntil calculate how long the system will remain compliant (expensive)
     * @param updateConsumer whether or not to use consumerCurator.update
     * @return Compliance status.
     */
    public ComplianceStatus getStatus(Consumer c, Date date, boolean calculateCompliantUntil,
        boolean updateConsumer) {

        return this.getStatus(c, null, date, calculateCompliantUntil, updateConsumer, false, true);
    }

    /**
     * Check compliance status for a consumer on a specific date.
     *
     * @param c Consumer to check.
     * @param date Date to check compliance status for.
     * @param calculateCompliantUntil calculate how long the system will remain compliant (expensive)
     * @param updateConsumer whether or not to use consumerCurator.update
     * @param calculateProductComplianceDateRanges calculate the individual compliance ranges for each product
     *        (also expensive)
     * @return Compliance status.
     */
    public ComplianceStatus getStatus(Consumer c, Collection<Entitlement> newEntitlements, Date date, boolean
        calculateCompliantUntil, boolean updateConsumer, boolean calculateProductComplianceDateRanges,
        boolean currentCompliance) {

        if (date == null) {
            date = new Date();
        }

        if (currentCompliance) {
            updateEntsOnStart(c);
        }

        List<Entitlement> allEnts = new LinkedList<Entitlement>(c.getEntitlements());
        if (newEntitlements != null) {
            allEnts.addAll(newEntitlements);
        }

        /*
         * Do not calculate compliance status for distributors and shares. It is prohibitively
         * expensive and meaningless
         */
        if (c.isManifestDistributor() || c.isShare()) {
            return new ComplianceStatus(new Date());
        }

        JsonJsContext args = new JsonJsContext(mapper);
        args.put("consumer", c);
        args.put("entitlements", allEnts);
        args.put("ondate", date);
        args.put("calculateCompliantUntil", calculateCompliantUntil);
        args.put("calculateProductComplianceDateRanges", calculateProductComplianceDateRanges);
        args.put("log", log, false);
        args.put("guestIds", c.getGuestIds());

        // Convert the JSON returned into a ComplianceStatus object:
        String json = jsRules.runJsFunction(String.class, "get_status", args);
        try {
            ComplianceStatus status = mapper.toObject(json, ComplianceStatus.class);
            for (ComplianceReason reason : status.getReasons()) {
                generator.setMessage(c, reason, status.getDate());
            }
            if (currentCompliance) {
                applyStatus(c, status, updateConsumer);
            }
            return status;
        }
        catch (Exception e) {
            throw new RuleExecutionException(e);
        }
    }

    public void updateEntsOnStart(Consumer c) {
        for (Entitlement ent : c.getEntitlements()) {
            if (!ent.isUpdatedOnStart() && ent.isValid()) {
                ent.setUpdatedOnStart(true);
                entCurator.merge(ent);
            }
        }
    }

    public void applyStatus(Consumer c, ComplianceStatus status, boolean updateConsumer) {
        String newHash = getComplianceStatusHash(status, c);
        boolean complianceChanged = !newHash.equals(c.getComplianceStatusHash());
        if (complianceChanged) {
            log.debug("Compliance has changed, sending Compliance event.");
            c.setComplianceStatusHash(newHash);
            eventSink.emitCompliance(c, c.getEntitlements(), status);
        }

        boolean entStatusChanged = !status.getStatus().equals(c.getEntitlementStatus());
        if (entStatusChanged) {
            c.setEntitlementStatus(status.getStatus());
        }

        if (updateConsumer && (complianceChanged || entStatusChanged)) {
            // Merge might work better here, but we use update in other places for this
            consumerCurator.update(c, false);
        }
    }

    public boolean isStackCompliant(Consumer consumer, String stackId, List<Entitlement> entsToConsider) {
        JsonJsContext args = new JsonJsContext(mapper);
        args.put("stack_id", stackId);
        args.put("consumer", consumer);
        args.put("entitlements", entsToConsider);
        args.put("log", log, false);
        args.put("guestIds", consumer.getGuestIds());

        return jsRules.runJsFunction(Boolean.class, "is_stack_compliant", args);
    }

    public boolean isEntitlementCompliant(Consumer consumer, Entitlement ent, Date onDate) {
        List<Entitlement> ents = entCurator.listByConsumerAndDate(consumer, onDate).list();

        JsonJsContext args = new JsonJsContext(mapper);
        args.put("consumer", consumer);
        args.put("entitlement", ent);
        args.put("entitlements", ents);
        args.put("log", log, false);
        args.put("guestIds", consumer.getGuestIds());

        return jsRules.runJsFunction(Boolean.class, "is_ent_compliant", args);
    }

    private String getComplianceStatusHash(ComplianceStatus status, Consumer consumer) {
        ComplianceStatusHasher hasher = new ComplianceStatusHasher(consumer, status);
        return hasher.hash();
    }


}
