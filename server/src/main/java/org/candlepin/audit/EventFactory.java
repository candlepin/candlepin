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
import org.candlepin.common.jackson.HateoasBeanPropertyFilter;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.jackson.PoolEventFilter;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.GuestId;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Rules;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.dto.Subscription;
import org.candlepin.policy.js.compliance.ComplianceStatus;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.ser.impl.SimpleBeanPropertyFilter;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.datatype.hibernate4.Hibernate4Module;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * EventFactory
 */
public class EventFactory {
    private static Logger log = LoggerFactory.getLogger(EventFactory.class);

    protected final PrincipalProvider principalProvider;
    private final ObjectMapper mapper;

    @Inject
    public EventFactory(PrincipalProvider principalProvider) {
        this.principalProvider = principalProvider;

        mapper = new ObjectMapper();

        // When serializing entity JSON for events, we want to use a reduced number
        // of fields nested objects, so enable the event and API HATEOAS filters:
        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        filterProvider.setFailOnUnknownId(false);
        filterProvider = filterProvider.addFilter("PoolFilter",
            new PoolEventFilter());
        filterProvider = filterProvider.addFilter("ConsumerFilter",
            new HateoasBeanPropertyFilter());
        filterProvider = filterProvider.addFilter("EntitlementFilter",
            new HateoasBeanPropertyFilter());
        filterProvider = filterProvider.addFilter("OwnerFilter",
            new HateoasBeanPropertyFilter());
        filterProvider = filterProvider.addFilter("IdentityCertificateFilter",
            SimpleBeanPropertyFilter.serializeAllExcept("cert", "key"));
        filterProvider = filterProvider.addFilter("EntitlementCertificateFilter",
            SimpleBeanPropertyFilter.serializeAllExcept("cert", "key"));
        filterProvider = filterProvider.addFilter("PoolAttributeFilter",
            SimpleBeanPropertyFilter.serializeAllExcept("created", "updated", "id"));
        filterProvider = filterProvider.addFilter("ProductPoolAttributeFilter",
            SimpleBeanPropertyFilter.serializeAllExcept("created", "updated", "productId", "id"));
        filterProvider = filterProvider.addFilter("SubscriptionCertificateFilter",
            SimpleBeanPropertyFilter.serializeAllExcept("cert", "key"));
        mapper.setFilters(filterProvider);

        Hibernate4Module hbm = new Hibernate4Module();
        hbm.enable(Hibernate4Module.Feature.FORCE_LAZY_LOADING);
        mapper.registerModule(hbm);

        AnnotationIntrospector primary = new JacksonAnnotationIntrospector();
        AnnotationIntrospector secondary = new JaxbAnnotationIntrospector(
            mapper.getTypeFactory());
        AnnotationIntrospector pair = new AnnotationIntrospectorPair(primary,
            secondary);
        mapper.setAnnotationIntrospector(pair);
    }

    public EventBuilder getEventBuilder(Target target, Type type) {
        return new EventBuilder(this, target, type);
    }

    public Event consumerCreated(Consumer newConsumer) {
        return getEventBuilder(Target.CONSUMER, Type.CREATED)
                .setNewEntity(newConsumer)
                .buildEvent();
    }

    public Event rulesUpdated(Rules oldRules, Rules newRules) {
        return getEventBuilder(Target.RULES, Type.MODIFIED)
                .setOldEntity(oldRules)
                .setNewEntity(newRules)
                .buildEvent();
    }

    public Event rulesDeleted(Rules deletedRules) {
        return getEventBuilder(Target.RULES, Type.DELETED)
                .setOldEntity(deletedRules)
                .buildEvent();
    }

    public Event activationKeyCreated(ActivationKey key) {
        return getEventBuilder(Target.ACTIVATIONKEY, Type.CREATED)
                .setNewEntity(key)
                .buildEvent();
    }

    public Event consumerModified(Consumer oldConsumer, Consumer newConsumer) {
        return getEventBuilder(Target.CONSUMER, Type.MODIFIED)
                .setOldEntity(oldConsumer)
                .setNewEntity(newConsumer)
                .buildEvent();
    }

    public Event consumerDeleted(Consumer oldConsumer) {
        return getEventBuilder(Target.CONSUMER, Type.DELETED)
                .setOldEntity(oldConsumer)
                .buildEvent();
    }

    public Event entitlementCreated(Entitlement e) {
        return getEventBuilder(Target.ENTITLEMENT, Type.CREATED).setNewEntity(e).buildEvent();
    }

    public Event entitlementDeleted(Entitlement e) {
        return getEventBuilder(Target.ENTITLEMENT, Type.DELETED).setOldEntity(e).buildEvent();
    }

    public Event entitlementExpired(Entitlement e) {
        return getEventBuilder(Target.ENTITLEMENT, Type.EXPIRED).setOldEntity(e).buildEvent();
    }

    public Event entitlementChanged(Entitlement e) {
        return getEventBuilder(Target.ENTITLEMENT, Type.MODIFIED).setNewEntity(e).buildEvent();
    }

    public Event ownerCreated(Owner newOwner) {
        return getEventBuilder(Target.OWNER, Type.CREATED)
                .setNewEntity(newOwner)
                .buildEvent();
    }

    public Event ownerModified(Owner newOwner) {
        return getEventBuilder(Target.OWNER, Type.MODIFIED)
                .setNewEntity(newOwner)
                .buildEvent();
    }

    public Event ownerDeleted(Owner owner) {
        return getEventBuilder(Target.OWNER, Type.DELETED)
                .setOldEntity(owner)
                .buildEvent();
    }

    // FIXME: Why do we need this?
    public Event ownerMigrated(Owner owner) {
        return ownerModified(owner);
    }

    public Event poolCreated(Pool newPool) {
        return getEventBuilder(Target.POOL, Type.CREATED)
                .setNewEntity(newPool)
                .buildEvent();
    }

    public Event poolDeleted(Pool pool) {
        return getEventBuilder(Target.POOL, Type.DELETED)
                .setOldEntity(pool)
                .buildEvent();
    }

    public Event exportCreated(Consumer consumer) {
        return getEventBuilder(Target.EXPORT, Type.CREATED)
                .setNewEntity(consumer)
                .buildEvent();
    }

    public Event importCreated(Owner owner) {
        return getEventBuilder(Target.IMPORT, Type.CREATED)
                .setNewEntity(owner)
                .buildEvent();
    }

    public Event guestIdCreated(GuestId guestId) {
        return getEventBuilder(Target.GUESTID, Type.CREATED)
                .setNewEntity(guestId)
                .buildEvent();
    }

    public Event guestIdDeleted(GuestId guestId) {
        return getEventBuilder(Target.GUESTID, Type.DELETED)
                .setOldEntity(guestId)
                .buildEvent();
    }

    public Event subscriptionExpired(Subscription subscription) {
        return getEventBuilder(Target.SUBSCRIPTION, Type.EXPIRED)
                 .setOldEntity(subscription)
                 .buildEvent();
    }

    public Event complianceCreated(Consumer consumer,
            Set<Entitlement> entitlements, ComplianceStatus compliance) {
        return new Event(Event.Type.CREATED, Event.Target.COMPLIANCE,
                consumer.getName(), principalProvider.get(),
                consumer.getOwner().getId(), consumer.getId(),
                consumer.getId(), null, buildComplianceDataJson(
                        consumer, entitlements, compliance), null, null);
    }

    // Jackson should think all 3 are root entities so hateoas doesn't bite us
    protected String buildComplianceDataJson(Consumer consumer,
            Set<Entitlement> entitlements, ComplianceStatus status) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"consumer\": ");
        sb.append(entityToJson(consumer));
        sb.append(", \"entitlements\": ");
        sb.append(entityToJson(entitlements));
        sb.append(", \"status\": ");
        sb.append(entityToJson(status));
        sb.append("}");
        return sb.toString();
    }

    protected String entityToJson(Object entity) {
        String newEntityJson = "";
        // TODO: Throw an auditing exception here

        // Drop data on consumer we do not want serialized, Jackson doesn't
        // seem to care about XmlTransient annotations when used here:

        try {
            newEntityJson = mapper.writeValueAsString(entity);
        }
        catch (Exception e) {
            log.warn("Unable to jsonify: " + entity);
            log.error("jsonification failed!", e);
        }
        return newEntityJson;
    }
}
