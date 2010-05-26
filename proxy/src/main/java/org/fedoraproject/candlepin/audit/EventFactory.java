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
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Owner;

import com.google.inject.Inject;

/**
 * EventFactory
 */
public class EventFactory {

    @Inject
    public EventFactory() {
    }

    public Event consumerCreated(Principal principal, Consumer newConsumer) {
        
        String newEntityJson = entityToJson(newConsumer);

        Event e = new Event(Event.Type.CREATED, Event.Target.CONSUMER, principal,
            principal.getOwner().getId(), newConsumer.getId(), null, newEntityJson);
        return e;
    }

    public Event consumerDeleted(Principal principal, Consumer oldConsumer) {
        String oldEntityJson = entityToJson(oldConsumer);

        Event e = new Event(Event.Type.DELETED, Event.Target.CONSUMER, principal,
            oldConsumer.getOwner().getId(), oldConsumer.getId(), oldEntityJson, null);
        return e;
    }

    public Event entitlementCreated(Principal principal, Entitlement e) {
        String newJson = entityToJson(e);
        Owner o = e.getOwner();
        Event event = new Event(Event.Type.CREATED, Event.Target.ENTITLEMENT, principal,
            o.getId(), e.getId(), null, newJson);
        return event;
    }

    public Event ownerCreated(Principal principal, Owner newOwner) {
        String newEntityJson = entityToJson(newOwner);
        Event e = new Event(Event.Type.CREATED, Event.Target.OWNER, principal,
            newOwner.getId(), newOwner.getId(), null, newEntityJson);
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
