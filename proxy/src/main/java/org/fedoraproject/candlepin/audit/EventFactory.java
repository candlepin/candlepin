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
package org.fedoraproject.candlepin.audit;

import java.io.IOException;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.AnnotationIntrospector;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.introspect.JacksonAnnotationIntrospector;
import org.codehaus.jackson.xc.JaxbAnnotationIntrospector;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.guice.PrincipalProvider;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;

import com.google.inject.Inject;

/**
 * EventFactory
 */
public class EventFactory {
    private PrincipalProvider principalProvider;
    private ObjectMapper mapper;

    @Inject
    public EventFactory(PrincipalProvider principalProvider) {
        this.principalProvider = principalProvider;
        
        AnnotationIntrospector primary = new JacksonAnnotationIntrospector();
        AnnotationIntrospector secondary = new JaxbAnnotationIntrospector();
        AnnotationIntrospector pair = new AnnotationIntrospector.Pair(primary, secondary);
        mapper = new ObjectMapper();
        mapper.getSerializationConfig().setAnnotationIntrospector(pair);
        mapper.getDeserializationConfig().setAnnotationIntrospector(pair);
    }

    public Event consumerCreated(Consumer newConsumer) {
        String newEntityJson = entityToJson(newConsumer);
        Principal principal = principalProvider.get();
        
        Event e = new Event(Event.Type.CREATED, Event.Target.CONSUMER, 
            principal, principal.getOwner().getId(), newConsumer.getId(),
            newConsumer.getId(), null, newEntityJson);
        return e;
    }
    
    public Event consumerModified(Consumer oldConsumer, Consumer newConsumer) {
        String oldEntityJson = entityToJson(oldConsumer);
        String newEntityJson = entityToJson(newConsumer);
        Principal principal = principalProvider.get();
        
        return new Event(Event.Type.MODIFIED, Event.Target.CONSUMER, 
            principal, principal.getOwner().getId(), oldConsumer.getId(),
            oldConsumer.getId(), oldEntityJson, newEntityJson);
    }

    public Event consumerDeleted(Consumer oldConsumer) {
        String oldEntityJson = entityToJson(oldConsumer);

        Event e = new Event(Event.Type.DELETED, Event.Target.CONSUMER, 
            principalProvider.get(), oldConsumer.getOwner().getId(), oldConsumer.getId(),
            oldConsumer.getId(), oldEntityJson, null);
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
        Owner owner = e.getOwner();
        return new Event(type, Event.Target.ENTITLEMENT,
            principalProvider.get(), owner.getId(), e.getConsumer().getId(), e
                .getId(), json, null);
    }

    public Event ownerCreated(Owner newOwner) {
        String newEntityJson = entityToJson(newOwner);
        Event e = new Event(Event.Type.CREATED, Event.Target.OWNER, 
            principalProvider.get(), newOwner.getId(), null,
            newOwner.getId(), null, newEntityJson);
        return e;
    }
    
    public Event poolCreated(Pool newPool) {
        String newEntityJson = entityToJson(newPool);
        Owner o = newPool.getOwner();
        Event e = new Event(Event.Type.CREATED, Event.Target.POOL, 
            principalProvider.get(), o.getId(), null, newPool.getId(), null, newEntityJson);
        return e;
    }
    
    public Event poolChangedFrom(Pool before) {
        Owner o = before.getOwner();
        Event e = new Event(Event.Type.MODIFIED, Event.Target.POOL, principalProvider.get(),
            o.getId(), null, before.getId(), entityToJson(before), null);
        return e;
    }
    
    public void poolChangedTo(Event e, Pool after) {
        e.setNewEntity(entityToJson(after));
    }
    
    public Event poolDeleted(Pool pool) {
        String oldJson = entityToJson(pool);
        Owner o = pool.getOwner();
        Event e = new Event(Event.Type.DELETED, Event.Target.POOL,
            principalProvider.get(), o.getId(), null, pool.getId(), oldJson, null);
        return e;
    }

    public Event ownerDeleted(Owner owner) {
        Event e = new Event(Event.Type.DELETED, Event.Target.OWNER, principalProvider.get(),
            owner.getId(), null, owner.getId(), entityToJson(owner), null);
        return e;
    }
    
    public Event exportCreated(Consumer consumer) {
        Principal principal = principalProvider.get();
        Event e = new Event(Event.Type.CREATED, Event.Target.EXPORT, principal, 
            principal.getOwner().getId(), consumer.getId(), null, null,
            entityToJson(consumer));
        return e;
    }
    
    public Event importCreated(Owner owner) {
        Principal principal = principalProvider.get();
        Event e = new Event(Event.Type.CREATED, Event.Target.IMPORT, principal, 
            owner.getId(), null, null, null, null);
        return e;
    }
    
    private String entityToJson(Object entity) {
        String newEntityJson = "";
        // TODO: Throw an auditing exception here
    
        // Drop data on consumer we do not want serialized, Jackson doesn't seem to
        // care about XmlTransient annotations when used here:
    
        try {
            newEntityJson = mapper.writeValueAsString(entity);
        }
        catch (JsonGenerationException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (JsonMappingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return newEntityJson;
    }
}
