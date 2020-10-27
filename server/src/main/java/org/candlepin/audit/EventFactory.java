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
package org.candlepin.audit;

import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.common.exceptions.IseException;
import org.candlepin.dto.ModelTranslator;
import org.candlepin.dto.api.v1.SystemPurposeComplianceStatusDTO;
import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.GuestId;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Rules;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.policy.SystemPurposeComplianceStatus;
import org.candlepin.policy.js.compliance.ComplianceReason;
import org.candlepin.policy.js.compliance.ComplianceStatus;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.name.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;



/**
 * EventFactory
 */
public class EventFactory {
    private static Logger log = LoggerFactory.getLogger(EventFactory.class);

    protected final PrincipalProvider principalProvider;
    private ObjectMapper mapper;
    private ModelTranslator modelTranslator;

    @Inject
    public EventFactory(PrincipalProvider principalProvider,
        @Named("EventFactoryObjectMapper") ObjectMapper objectMapper,
        ModelTranslator modelTranslator) {

        this.mapper = Objects.requireNonNull(objectMapper);
        this.principalProvider = Objects.requireNonNull(principalProvider);
        this.modelTranslator = Objects.requireNonNull(modelTranslator);
    }

    public EventBuilder getEventBuilder(Target target, Type type) {
        return new EventBuilder(this, target, type);
    }

    public Event consumerCreated(Consumer newConsumer) {
        return getEventBuilder(Target.CONSUMER, Type.CREATED)
            .setEventData(newConsumer)
            .buildEvent();
    }

    public Event rulesUpdated(Rules oldRules, Rules newRules) {
        return getEventBuilder(Target.RULES, Type.MODIFIED)
            .setEventData(oldRules, newRules)
            .buildEvent();
    }

    public Event rulesDeleted(Rules deletedRules) {
        return getEventBuilder(Target.RULES, Type.DELETED)
            .setEventData(deletedRules)
            .buildEvent();
    }

    public Event activationKeyCreated(ActivationKey key) {
        return getEventBuilder(Target.ACTIVATIONKEY, Type.CREATED)
            .setEventData(key)
            .buildEvent();
    }

    public Event consumerDeleted(Consumer oldConsumer) {
        return getEventBuilder(Target.CONSUMER, Type.DELETED)
            .setEventData(oldConsumer)
            .buildEvent();
    }

    public Event entitlementCreated(Entitlement e) {
        return getEventBuilder(Target.ENTITLEMENT, Type.CREATED)
            .setEventData(e)
            .buildEvent();
    }

    public Event entitlementDeleted(Entitlement e) {
        return getEventBuilder(Target.ENTITLEMENT, Type.DELETED)
            .setEventData(e)
            .buildEvent();
    }

    public Event entitlementExpired(Entitlement e) {
        return getEventBuilder(Target.ENTITLEMENT, Type.EXPIRED)
            .setEventData(e)
            .buildEvent();
    }

    public Event entitlementChanged(Entitlement e) {
        return getEventBuilder(Target.ENTITLEMENT, Type.MODIFIED)
            .setEventData(e)
            .buildEvent();
    }

    public Event ownerCreated(Owner newOwner) {
        return getEventBuilder(Target.OWNER, Type.CREATED)
            .setEventData(newOwner)
            .buildEvent();
    }

    public Event ownerModified(Owner newOwner) {
        return getEventBuilder(Target.OWNER, Type.MODIFIED)
            .setEventData(newOwner)
            .buildEvent();
    }

    public Event ownerDeleted(Owner owner) {
        return getEventBuilder(Target.OWNER, Type.DELETED)
            .setEventData(owner)
            .buildEvent();
    }

    public Event poolCreated(Pool newPool) {
        return getEventBuilder(Target.POOL, Type.CREATED)
            .setEventData(newPool)
            .buildEvent();
    }

    public Event poolDeleted(Pool pool) {
        return getEventBuilder(Target.POOL, Type.DELETED)
            .setEventData(pool)
            .buildEvent();
    }

    public Event exportCreated(Consumer consumer) {
        return getEventBuilder(Target.EXPORT, Type.CREATED)
            .setEventData(consumer)
            .buildEvent();
    }

    public Event importCreated(Owner owner) {
        return getEventBuilder(Target.IMPORT, Type.CREATED)
            .setEventData(owner)
            .buildEvent();
    }

    public Event guestIdCreated(GuestId guestId) {
        return getEventBuilder(Target.GUESTID, Type.CREATED)
            .setEventData(guestId)
            .buildEvent();
    }

    public Event guestIdDeleted(GuestId guestId) {
        return getEventBuilder(Target.GUESTID, Type.DELETED)
            .setEventData(guestId)
            .buildEvent();
    }

    public Event subscriptionExpired(SubscriptionDTO subscription) {
        return getEventBuilder(Target.SUBSCRIPTION, Type.EXPIRED)
             .setEventData(subscription)
             .buildEvent();
    }

    public Event ownerContentAccessModeChanged(Owner owner) {
        return getEventBuilder(Target.OWNER_CONTENT_ACCESS_MODE, Type.MODIFIED)
            .setEventData(owner)
            .buildEvent();
    }

    public Event complianceCreated(Consumer consumer, ComplianceStatus compliance) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("status", compliance.getStatus());

        List<Map<String, String>> reasons = new ArrayList<>(compliance.getReasons().size());
        for (ComplianceReason reason : compliance.getReasons()) {
            reasons.add(ImmutableMap.of(
                "productName", reason.getAttributes().get(ComplianceReason.Attributes.MARKETING_NAME),
                "message", reason.getMessage()
            ));
        }
        eventData.put("reasons", reasons);
        try {
            String eventDataJson = mapper.writeValueAsString(eventData);
            // Instead of an internal db id, compliance.created events now use
            // UUID for the 'consumerId' and 'entityId' fields, since Katello
            // is concerned only with the consumer UUID field.
            return new Event(Event.Type.CREATED, Event.Target.COMPLIANCE,
                consumer.getName(), principalProvider.get(), consumer.getOwnerId(), consumer.getUuid(),
                consumer.getUuid(), eventDataJson, null, null);
        }
        catch (JsonProcessingException e) {
            log.error("Error while building JSON for compliance.created event.", e);
            throw new IseException("Error while building JSON for compliance.created event.", e);
        }
    }

    public Event complianceCreated(Consumer consumer, SystemPurposeComplianceStatus compliance) {

        // TODO: We *should* have an event-specific set of DTOs if we're going to output model objects
        // directly like this. However, at the time of writing, the API DTOs will be sufficient.

        SystemPurposeComplianceStatusDTO dto = this.modelTranslator
            .translate(compliance, SystemPurposeComplianceStatusDTO.class);

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("status", dto.getStatus());
        eventData.put("reasons", dto.getReasons());
        eventData.put("nonCompliantSLA", dto.getNonCompliantSLA());
        eventData.put("nonCompliantRole", dto.getNonCompliantRole());
        eventData.put("nonCompliantUsage", dto.getNonCompliantUsage());
        eventData.put("nonCompliantAddOns", dto.getNonCompliantAddOns());
        eventData.put("compliantSLA", dto.getCompliantSLA());
        eventData.put("compliantRole", dto.getCompliantRole());
        eventData.put("compliantUsage", dto.getCompliantUsage());
        eventData.put("compliantAddOns", dto.getCompliantAddOns());

        try {
            String eventDataJson = mapper.writeValueAsString(eventData);
            // Instead of an internal db id, compliance.created events now use
            // UUID for the 'consumerId' and 'entityId' fields, since Katello
            // is concerned only with the consumer UUID field.
            return new Event(Event.Type.CREATED, Target.SYSTEM_PURPOSE_COMPLIANCE,
                consumer.getName(), principalProvider.get(), consumer.getOwnerId(), consumer.getUuid(),
                consumer.getUuid(), eventDataJson, null, null);
        }
        catch (JsonProcessingException e) {
            log.error("Error while building JSON for system purpose compliance.created event.", e);
            throw new IseException("Error while building JSON for system purpose compliance.created event.",
                e);
        }
    }

}
