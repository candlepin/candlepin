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
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.rules.v1.ConsumerDTO;
import org.candlepin.dto.rules.v1.EntitlementDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerType;
import org.candlepin.model.ConsumerType.ConsumerTypeEnum;
import org.candlepin.model.ConsumerTypeCurator;
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
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;



/**
 * ComplianceRules
 *
 * A class used to check consumer compliance status.
 */
public class ComplianceRules {
    private static Logger log = LoggerFactory.getLogger(ComplianceRules.class);

    private JsRunner jsRules;
    private EntitlementCurator entCurator;
    private StatusReasonMessageGenerator generator;
    private EventSink eventSink;
    private ConsumerCurator consumerCurator;
    private ConsumerTypeCurator consumerTypeCurator;
    private RulesObjectMapper mapper;
    private ModelTranslator translator;

    @Inject
    public ComplianceRules(JsRunner jsRules, EntitlementCurator entCurator,
        StatusReasonMessageGenerator generator, EventSink eventSink, ConsumerCurator consumerCurator,
        ConsumerTypeCurator consumerTypeCurator, RulesObjectMapper mapper, ModelTranslator translator) {

        this.jsRules = jsRules;
        this.entCurator = entCurator;
        this.generator = generator;
        this.eventSink = eventSink;
        this.consumerCurator = consumerCurator;
        this.consumerTypeCurator = consumerTypeCurator;
        this.mapper = mapper;
        this.translator = translator;

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
     * @param consumer Consumer to check.
     * @param date Date to check compliance status for.
     * @param calculateCompliantUntil calculate how long the system will remain compliant (expensive)
     * @param updateConsumer whether or not to use consumerCurator.update
     * @param calculateProductComplianceDateRanges calculate the individual compliance ranges for each product
     *        (also expensive)
     * @return Compliance status.
     */
    @SuppressWarnings("checkstyle:indentation")
    public ComplianceStatus getStatus(Consumer consumer, Collection<Entitlement> newEntitlements, Date date,
        boolean calculateCompliantUntil, boolean updateConsumer, boolean calculateProductComplianceDateRanges,
        boolean currentCompliance) {

        if (date == null) {
            date = new Date();
        }

        if (currentCompliance) {
            updateEntsOnStart(consumer);
        }

        Stream<EntitlementDTO> entStream = Stream.concat(
            newEntitlements != null ? newEntitlements.stream() : Stream.empty(),
            consumer.getEntitlements() != null ? consumer.getEntitlements().stream() : Stream.empty())
                .map(this.translator.getStreamMapper(Entitlement.class, EntitlementDTO.class));

        // Do not calculate compliance status for distributors and shares. It is prohibitively
        // expensive and meaningless
        ConsumerType ctype = this.consumerTypeCurator.getConsumerType(consumer);
        if (ctype != null && (ctype.isManifest() || ctype.isType(ConsumerTypeEnum.SHARE))) {
            return new ComplianceStatus(new Date());
        }

        JsonJsContext args = new JsonJsContext(mapper);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("entitlements", entStream.collect(Collectors.toSet()));
        args.put("ondate", date);
        args.put("calculateCompliantUntil", calculateCompliantUntil);
        args.put("calculateProductComplianceDateRanges", calculateProductComplianceDateRanges);
        args.put("log", log, false);
        args.put("guestIds", consumer.getGuestIds());

        // Convert the JSON returned into a ComplianceStatus object:
        String json = jsRules.runJsFunction(String.class, "get_status", args);
        try {
            ComplianceStatus status = mapper.toObject(json, ComplianceStatus.class);

            for (ComplianceReason reason : status.getReasons()) {
                generator.setMessage(consumer, reason, status.getDate());
            }

            if (currentCompliance) {
                applyStatus(consumer, status, updateConsumer);
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
            eventSink.emitCompliance(c, status);
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

    @SuppressWarnings("checkstyle:indentation")
    public boolean isStackCompliant(Consumer consumer, String stackId, List<Entitlement> entsToConsider) {
        Stream<EntitlementDTO> entStream = entsToConsider == null ? Stream.empty() :
            entsToConsider.stream()
                .map(this.translator.getStreamMapper(Entitlement.class, EntitlementDTO.class));

        JsonJsContext args = new JsonJsContext(mapper);
        args.put("stack_id", stackId);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("entitlements", entStream.collect(Collectors.toSet()));
        args.put("log", log, false);
        args.put("guestIds", consumer.getGuestIds());

        return jsRules.runJsFunction(Boolean.class, "is_stack_compliant", args);
    }

    public boolean isEntitlementCompliant(Consumer consumer, Entitlement ent, Date onDate) {
        List<Entitlement> ents = entCurator.listByConsumerAndDate(consumer, onDate).list();

        Stream<EntitlementDTO> entStream = ents == null ? Stream.empty() :
            ents.stream().map(this.translator.getStreamMapper(Entitlement.class, EntitlementDTO.class));

        JsonJsContext args = new JsonJsContext(mapper);
        args.put("consumer", this.translator.translate(consumer, ConsumerDTO.class));
        args.put("entitlement", this.translator.translate(ent, EntitlementDTO.class));
        args.put("entitlements", entStream.collect(Collectors.toSet()));
        args.put("log", log, false);
        args.put("guestIds", consumer.getGuestIds());

        return jsRules.runJsFunction(Boolean.class, "is_ent_compliant", args);
    }

    private String getComplianceStatusHash(ComplianceStatus status, Consumer consumer) {
        ComplianceStatusHasher hasher = new ComplianceStatusHasher(consumer, status);
        return hasher.hash();
    }


}
