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

    @Inject
    public EventFactory(PrincipalProvider principalProvider) {
        this.principalProvider = principalProvider;
    }

    public Event consumerCreated(Consumer newConsumer) {
        
        String newEntityJson = entityToJson(newConsumer);
        Principal principal = principalProvider.get();
        
        Event e = new Event(Event.Type.CREATED, Event.Target.CONSUMER, 
            principal, principal.getOwner().getId(), 
            newConsumer.getId(), null, newEntityJson);
        return e;
    }

    public Event consumerDeleted(Consumer oldConsumer) {
        String oldEntityJson = entityToJson(oldConsumer);

        Event e = new Event(Event.Type.DELETED, Event.Target.CONSUMER, 
            principalProvider.get(), oldConsumer.getOwner().getId(), 
            oldConsumer.getId(), oldEntityJson, null);
        return e;
    }

    public Event entitlementCreated(Entitlement e) {
        String newJson = entityToJson(e);
        Owner o = e.getOwner();
        Event event = new Event(Event.Type.CREATED, Event.Target.ENTITLEMENT, 
            principalProvider.get(), o.getId(), e.getId(), null, newJson);
        return event;
    }
    
    public Event entitlementDeleted(Entitlement e) {
        String json = entityToJson(e);
        Owner o = e.getOwner();
        Event event = new Event(Event.Type.DELETED, Event.Target.ENTITLEMENT, 
            principalProvider.get(), o.getId(), e.getId(), json, null);
        return event;
    }

    public Event ownerCreated(Owner newOwner) {
        String newEntityJson = entityToJson(newOwner);
        Event e = new Event(Event.Type.CREATED, Event.Target.OWNER, 
            principalProvider.get(), newOwner.getId(), 
            newOwner.getId(), null, newEntityJson);
        return e;
    }
    
    public Event poolCreated(Pool newPool) {
        String newEntityJson = entityToJson(newPool);
        Owner o = newPool.getOwner();
        Event e = new Event(Event.Type.CREATED, Event.Target.POOL, 
            principalProvider.get(), o.getId(), newPool.getId(), null, newEntityJson);
        return e;
    }
    
    public Event poolQuantityChangedFrom(Pool before) {
        Owner o = before.getOwner();
        Event e = new Event(Event.Type.MODIFIED, Event.Target.POOL, principalProvider.get(),
            o.getId(), before.getId(), entityToJson(before), null);
        return e;
    }
    
    public void poolQuantityChangedTo(Event e, Pool after) {
        e.setNewEntity(entityToJson(after));
    }
    
    public Event ownerDeleted(Owner owner) {
        Event e = new Event(Event.Type.DELETED, Event.Target.OWNER, principalProvider.get(),
            owner.getId(), owner.getId(), entityToJson(owner), null);
        return e;
    }
    
    private String entityToJson(Object entity) {
        AnnotationIntrospector primary = new JacksonAnnotationIntrospector();
        AnnotationIntrospector secondary = new JaxbAnnotationIntrospector();
        AnnotationIntrospector pair = new AnnotationIntrospector.Pair(primary, secondary);
            
        ObjectMapper mapper = new ObjectMapper();
        mapper.getSerializationConfig().setAnnotationIntrospector(pair);
        mapper.getDeserializationConfig().setAnnotationIntrospector(pair);
    
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
