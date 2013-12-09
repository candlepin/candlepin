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

import org.candlepin.auth.Principal;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.jackson.HateoasBeanPropertyFilter;
import org.candlepin.model.ActivationKey;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.GuestId;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Rules;
import org.candlepin.model.Subscription;

import com.fasterxml.jackson.databind.AnnotationIntrospector;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.introspect.AnnotationIntrospectorPair;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import com.fasterxml.jackson.databind.ser.impl.SimpleFilterProvider;
import com.fasterxml.jackson.module.jaxb.JaxbAnnotationIntrospector;
import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EventFactory
 */
public class EventFactory {
    private final PrincipalProvider principalProvider;
    private final ObjectMapper mapper;
    private static Logger logger = LoggerFactory.getLogger(EventFactory.class);

    @Inject
    public EventFactory(PrincipalProvider principalProvider) {
        this.principalProvider = principalProvider;

        mapper = new ObjectMapper();

        // When serializing entity JSON for events, we want to use a reduced number
        // of fields nested objects, so enable the event and API HATEOAS filters:
        SimpleFilterProvider filterProvider = new SimpleFilterProvider();
        filterProvider.setFailOnUnknownId(false);
        filterProvider = filterProvider.addFilter("PoolFilter",
            new HateoasBeanPropertyFilter());
        filterProvider = filterProvider.addFilter("ConsumerFilter",
            new HateoasBeanPropertyFilter());
        filterProvider = filterProvider.addFilter("EntitlementFilter",
            new HateoasBeanPropertyFilter());
        filterProvider = filterProvider.addFilter("OwnerFilter",
            new HateoasBeanPropertyFilter());
        mapper.setFilters(filterProvider);

        AnnotationIntrospector primary = new JacksonAnnotationIntrospector();
        AnnotationIntrospector secondary = new JaxbAnnotationIntrospector(
            mapper.getTypeFactory());
        AnnotationIntrospector pair = new AnnotationIntrospectorPair(primary,
            secondary);
        mapper.setAnnotationIntrospector(pair);
    }

    public Event consumerCreated(Consumer newConsumer) {
        String newEntityJson = entityToJson(newConsumer);
        Principal principal = principalProvider.get();
        Event e = new Event(Event.Type.CREATED, Event.Target.CONSUMER,
            newConsumer.getName(), principal, newConsumer.getOwner().getId(),
            newConsumer.getId(), newConsumer.getId(), null, newEntityJson,
            null, null);
        return e;
    }

    public Event rulesUpdated(Rules oldRules, Rules newRules) {
        String olds = entityToJson(oldRules);
        String news = entityToJson(newRules);
        Principal principal = principalProvider.get();
        Event e = new Event(Event.Type.MODIFIED, Event.Target.RULES,
            newRules.getVersion(), principal, null,
            null, "" + (String) newRules.getId(),
            olds, news, null, null);
        return e;
    }

    public Event rulesDeleted(Rules deletedRules) {
        String oldEntityJson = entityToJson(deletedRules);
        Principal principal = principalProvider.get();
        Event e = new Event(Event.Type.DELETED, Event.Target.RULES,
            deletedRules.getVersion(), principal, null,
            null, "" + (String) deletedRules.getId(),
            oldEntityJson, null, null, null);
        return e;
    }

    public Event activationKeyCreated(ActivationKey key) {
        String newEntityJson = entityToJson(key);
        Principal principal = principalProvider.get();

        Event e = new Event(Event.Type.CREATED, Event.Target.ACTIVATIONKEY,
            key.getName(), principal, key.getOwner().getId(),
            null, key.getId(), null, newEntityJson,
            null, null);
        return e;
    }

    public Event consumerModified(Consumer newConsumer) {
        String newEntityJson = entityToJson(newConsumer);
        Principal principal = principalProvider.get();

        return new Event(Event.Type.MODIFIED, Event.Target.CONSUMER,
            newConsumer.getName(), principal, newConsumer.getOwner().getId(),
            newConsumer.getId(), newConsumer.getId(), null, newEntityJson,
            null, null);
    }

    public Event consumerModified(Consumer oldConsumer, Consumer newConsumer) {
        String oldEntityJson = entityToJson(oldConsumer);
        String newEntityJson = entityToJson(newConsumer);
        Principal principal = principalProvider.get();

        return new Event(Event.Type.MODIFIED, Event.Target.CONSUMER,
            oldConsumer.getName(), principal, oldConsumer.getOwner().getId(),
            oldConsumer.getId(), oldConsumer.getId(), oldEntityJson, newEntityJson,
            null, null);
    }

    public Event consumerDeleted(Consumer oldConsumer) {
        String oldEntityJson = entityToJson(oldConsumer);

        Event e = new Event(Event.Type.DELETED, Event.Target.CONSUMER,
            oldConsumer.getName(), principalProvider.get(), oldConsumer
                .getOwner().getId(), oldConsumer.getId(), oldConsumer.getId(),
            oldEntityJson, null, null, null);
        return e;
    }

    public Event entitlementCreated(Entitlement e) {
        return entitlementEvent(e, Event.Type.CREATED);
    }

    public Event entitlementDeleted(Entitlement e) {
        return entitlementEvent(e, Event.Type.DELETED);
    }

    public Event entitlementChanged(Entitlement e) {
        return entitlementEvent(e, Event.Type.MODIFIED);
    }

    private Event entitlementEvent(Entitlement e, Event.Type type) {
        String json = entityToJson(e);
        String old = null, latest = null;
        Owner owner = e.getOwner();
        if (type == Event.Type.DELETED) {
            old = json;
        }
        else {
            latest = json;
        }
        return new Event(type, Event.Target.ENTITLEMENT, e.getPool()
            .getProductName(), principalProvider.get(), owner.getId(), e
            .getConsumer().getId(), e.getId(), old, latest,
            e.getPool().getId(), Event.ReferenceType.POOL);
    }

    public Event ownerCreated(Owner newOwner) {
        String newEntityJson = entityToJson(newOwner);
        Event e = new Event(Event.Type.CREATED, Event.Target.OWNER,
            newOwner.getDisplayName(), principalProvider.get(),
            newOwner.getId(), null, newOwner.getId(), null, newEntityJson,
            null, null);
        return e;
    }

    public Event ownerModified(Owner newOwner) {
        String newEntityJson = entityToJson(newOwner);
        return new Event(Event.Type.MODIFIED, Event.Target.OWNER,
            newOwner.getDisplayName(), principalProvider.get(),
            newOwner.getId(), null, newOwner.getId(), null, newEntityJson,
            null, null);
    }


    public Event ownerDeleted(Owner owner) {
        Event e = new Event(Event.Type.DELETED, Event.Target.OWNER,
            owner.getDisplayName(), principalProvider.get(), owner.getId(),
            null, owner.getId(), entityToJson(owner), null, null, null);
        return e;
    }

    public Event ownerMigrated(Owner owner) {
        String ownerJson = entityToJson(owner);
        Event e = new Event(Event.Type.MODIFIED, Event.Target.OWNER,
            owner.getDisplayName(), principalProvider.get(), owner.getId(),
            null, owner.getId(), ownerJson, ownerJson, null, null);

        return e;
    }

    public Event poolCreated(Pool newPool) {
        String newEntityJson = entityToJson(newPool);
        Owner o = newPool.getOwner();
        Event e = new Event(Event.Type.CREATED, Event.Target.POOL,
            newPool.getProductName(), principalProvider.get(), o.getId(), null,
            newPool.getId(), null, newEntityJson, null, null);
        return e;
    }

    public Event poolChangedFrom(Pool before) {
        Owner o = before.getOwner();
        Event e = new Event(Event.Type.MODIFIED, Event.Target.POOL,
            before.getProductName(), principalProvider.get(), o.getId(), null,
            before.getId(), entityToJson(before), null, null, null);
        return e;
    }

    public void poolChangedTo(Event e, Pool after) {
        e.setNewEntity(entityToJson(after));
    }

    public Event poolDeleted(Pool pool) {
        String oldJson = entityToJson(pool);
        Owner o = pool.getOwner();
        Event e = new Event(Event.Type.DELETED, Event.Target.POOL,
            pool.getProductName(), principalProvider.get(), o.getId(), null,
            pool.getId(), oldJson, null, null, null);
        return e;
    }

    public Event exportCreated(Consumer consumer) {
        Principal principal = principalProvider.get();
        Event e = new Event(Event.Type.CREATED, Event.Target.EXPORT, consumer.getName(),
            principal, consumer.getOwner().getId(), consumer.getId(),
            consumer.getId(), null, entityToJson(consumer),
            null, null);
        return e;
    }

    public Event importCreated(Owner owner) {
        Principal principal = principalProvider.get();
        Event e = new Event(Event.Type.CREATED, Event.Target.IMPORT,
            owner.getDisplayName(), principal, owner.getId(), null,
            owner.getId(), null, entityToJson(owner), null, null);
        return e;
    }

    public Event subscriptionCreated(Subscription subscription) {
        Principal principal = principalProvider.get();

        Event e = new Event(Event.Type.CREATED, Event.Target.SUBSCRIPTION,
            subscription.getProduct().getName(), principal,
            subscription.getOwner().getId(), null, subscription.getId(), null,
            entityToJson(subscription), null, null);
        return e;
    }

    public Event subscriptionModified(Subscription oldSub, Subscription newSub) {
        String olds = entityToJson(oldSub);
        String news = entityToJson(newSub);
        Principal principal = principalProvider.get();
        return new Event(Event.Type.MODIFIED, Event.Target.SUBSCRIPTION,
            oldSub.getProduct().getName(), principal, newSub.getOwner().getId(),
            null, newSub.getId(), olds, news, null, null);
    }

    public Event subscriptionDeleted(Subscription todelete) {
        String oldJson = entityToJson(todelete);
        Owner o = todelete.getOwner();
        Event e = new Event(Event.Type.DELETED, Event.Target.SUBSCRIPTION,
            todelete.getProduct().getName(), principalProvider.get(),
            o.getId(), null, todelete.getId(), oldJson, null, null, null);
        return e;
    }

    public Event guestIdCreated(Consumer consumer, GuestId guestId) {
        return this.createGuestIdEvent(consumer, guestId, Event.Type.CREATED);
    }

    public Event guestIdDeleted(Consumer consumer, GuestId guestId) {
        return this.createGuestIdEvent(consumer, guestId, Event.Type.DELETED);
    }

    private Event createGuestIdEvent(Consumer affectedConsumer, GuestId affectedGuestId,
        Event.Type eventType) {
        Event event = new Event(eventType, Event.Target.GUESTID,
            affectedGuestId.getGuestId(), principalProvider.get(),
            affectedConsumer.getOwner().getId(), affectedConsumer.getId(),
            // we use getGuestId here since we may not have a guestID obj with an ID yet
            affectedGuestId.getGuestId(), null, entityToJson(affectedGuestId), null, null);
        return event;
    }

    private String entityToJson(Object entity) {
        String newEntityJson = "";
        // TODO: Throw an auditing exception here

        // Drop data on consumer we do not want serialized, Jackson doesn't
        // seem to care about XmlTransient annotations when used here:

        try {
            newEntityJson = mapper.writeValueAsString(entity);
        }
        catch (Exception e) {
            logger.warn("Unable to jsonify: " + entity);
            logger.error("jsonification failed!", e);
        }
        return newEntityJson;
    }
}
