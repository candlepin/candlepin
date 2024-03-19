/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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
import org.candlepin.exceptions.IseException;
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

    private Event event;

    public EventBuilder() {
    }

    public EventBuilder(EventFactory factory, Target target, Type type) {
        event = new Event(type, target, factory.principalProvider.get().getData());
    }

    /**
     * Utility method used to pass in the old and new version of the modified
     * entity for {@link Type#MODIFIED} events.
     * @param oldEntity The target entity before modification
     * @param newEntity The target entity after modification
     * @return The builder object
     */
    public EventBuilder setEventData(Eventful oldEntity, Eventful newEntity) {
        if (!event.getType().equals(Type.MODIFIED)) {
            throw new IseException("This method is only for type MODIFIED Events.");
        }
        setEventData(oldEntity);
        return setEventData(newEntity);
    }

    /**
     * This method is used with any type of event and any target entity.
     * <p>
     * Note: For {@link Type#MODIFIED} events, it can be called twice consecutively
     * to first pass in the original, and then the updated entity. Alternatively,
     * {@link #setEventData(Eventful, Eventful)} can be used in the same use case.
     * </p>
     * @param entity The target entity of the Event
     * @return The builder object
     */
    public EventBuilder setEventData(Eventful entity) {
        if (entity != null) {
            // Be careful to check for null before setting so we don't overwrite anything useful
            if (entity instanceof Named named && named.getName() != null) {
                event.setTargetName(named.getName());
            }

            if (entity instanceof Owned owned) {
                String ownerKey = owned.getOwnerKey();
                if (ownerKey != null) {
                    event.setOwnerKey(ownerKey);
                }
            }

            if (entity instanceof Entitlement ent) {
                event.setReferenceType(Event.ReferenceType.POOL);
                Pool referencedPool = ent.getPool();

                if (referencedPool != null && referencedPool.getId() != null) {
                    event.setReferenceId(referencedPool.getId());
                }
            }

            if (entity.getId() != null) {
                event.setEntityId((String) entity.getId());

                if (entity instanceof ConsumerProperty consumerProperty) {
                    Consumer owningConsumer = consumerProperty.getConsumer();
                    if (owningConsumer != null && owningConsumer.getUuid() != null) {
                        event.setConsumerUuid(owningConsumer.getUuid());
                    }
                }
            }

            if (event.getTarget().equals(Target.POOL) && event.getType().equals(Type.CREATED)) {
                Map<String, Object> eventData = new HashMap<>();
                eventData.put("subscriptionId", ((Pool) entity).getSubscriptionId());
                event.setEventData(eventData);
            }

            if (event.getTarget().equals(Target.OWNER_CONTENT_ACCESS_MODE) &&
                event.getType().equals(Type.MODIFIED)) {
                Map<String, Object> eventData = new HashMap<>();
                eventData.put("contentAccessMode", ((Owner) entity).getContentAccessMode());
                event.setEventData(eventData);
            }
        }

        return this;
    }

    public Event buildEvent() {
        return event;
    }
}
