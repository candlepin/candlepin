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

import org.codehaus.jackson.map.ObjectMapper;
import org.fedoraproject.candlepin.audit.Event.EventType;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.EventIdCurator;

import com.google.inject.Inject;

/**
 * EventFactory
 */
public class EventFactory {

    private EventIdCurator eventIdCurator;

    @Inject
    public EventFactory(EventIdCurator eventIdCurator) {
        this.eventIdCurator = eventIdCurator;
    }

    public Event consumerCreated(Principal principal, Consumer newConsumer) {
        ObjectMapper mapper = new ObjectMapper();
        String newEntityJson = "";
        // TODO: Throw an auditing exception here

        // Drop data on consumer we do not want serialized, Jackson doesn't seem to
        // care about XmlTransient annotations when used here:
//
//        try {
//            newEntityJson = mapper.writeValueAsString(newConsumer);
//        }
//        catch (JsonGenerationException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
//        catch (JsonMappingException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }
//        catch (IOException e) {
//            // TODO Auto-generated catch block
//            e.printStackTrace();
//        }

        Event e = new Event(EventType.CONSUMER_CREATED, principal, newConsumer.getId(),
            null, newEntityJson);
        // TODO: Move somewhere more widespread:
        e.setId(eventIdCurator.getNextEventId());
        return e;
    }
}
