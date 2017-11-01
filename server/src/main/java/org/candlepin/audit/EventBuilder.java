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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.common.exceptions.IseException;
import org.candlepin.model.Consumer;
import org.candlepin.model.ConsumerProperty;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Eventful;
import org.candlepin.model.Named;
import org.candlepin.model.Owned;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * ConsumerEventBuilder Allows us to easily build a consumer modified
 * event one piece at a time.
 *
 */
public class EventBuilder {

    private static final Logger log = LoggerFactory.getLogger(EventBuilder.class);

    private final EventFactory factory;
    private final ObjectMapper mapper;

    private Event event;

    public EventBuilder(EventFactory factory, Target target, Type type) {
        this.factory = factory;
        this.mapper = new ObjectMapper();

        event = new Event(type, target, null, factory.principalProvider.get(),
                null, null, null, null, null, null, null);
    }

    /*  Implementation note (2017/10/16):
        This method is currently used instead of setOldEntity, since oldEntity was removed,
        and it will also be used in the future instead of setNewEntity once newEntity is removed.
        This is part of simplifying the data Events hold.
     */
    public EventBuilder setEventData(Eventful entity) {
        if (entity != null) {
            // Be careful to check for null before setting so we don't overwrite anything useful
            if (entity instanceof Named && ((Named) entity).getName() != null) {
                event.setTargetName(((Named) entity).getName());
            }
            if (entity instanceof Owned) {
                Owner entityOwner = ((Owned) entity).getOwner();
                if (entityOwner != null && entityOwner.getId() != null) {
                    event.setOwnerId(entityOwner.getId());
                }
            }
            if (entity instanceof Entitlement) {
                event.setReferenceType(Event.ReferenceType.POOL);
                Pool referencedPool = ((Entitlement) entity).getPool();
                if (referencedPool != null && referencedPool.getId() != null) {
                    event.setReferenceId(referencedPool.getId());
                }
            }
            if (entity.getId() != null) {
                event.setEntityId((String) entity.getId());
                if (entity instanceof ConsumerProperty) {
                    Consumer owningConsumer = ((ConsumerProperty) entity).getConsumer();
                    if (owningConsumer != null && owningConsumer.getId() != null) {
                        event.setConsumerId(owningConsumer.getId());
                    }
                }
            }
            if (event.getTarget().equals(Target.POOL) && event.getType().equals(Type.CREATED)) {
                Map<String, String> eventData = new HashMap<String, String>();
                eventData.put("subscriptionId", ((Pool) entity).getSubscriptionId());
                try {
                    event.setEventData(mapper.writeValueAsString(eventData));
                }
                catch (JsonProcessingException e) {
                    log.error("Error while building JSON for pool.created event.", e);
                    throw new IseException("Error while building JSON for pool.created event.");
                }
            }
        }
        return this;
    }

    public EventBuilder setNewEntity(Eventful updated) {
        if (updated != null) {
            if (event.getType() == Type.DELETED || event.getType() == Type.EXPIRED) {
                throw new IllegalArgumentException("You cannot set the new entity for a deletion event");
            }
            setEventData(updated);
            event.setNewEntity(factory.entityToJson(updated));
        }
        return this;
    }

    public Event buildEvent() {
        return event;
    }
}
