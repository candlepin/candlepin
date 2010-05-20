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

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

import org.codehaus.jackson.JsonGenerationException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.fedoraproject.candlepin.audit.Event.EventType;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.model.Consumer;

/**
 * ConsumerEvent
 */
@Entity
@DiscriminatorValue("consumer")
public class ConsumerEvent extends Event {

    public ConsumerEvent(EventType type, Principal principal,
        Long entityId, Consumer oldEntity, Consumer newEntity) {

        // TODO: Verify type is one of the consumer types?

        super(type, principal, entityId, null, null);
        ObjectMapper mapper = new ObjectMapper();
        // TODO: What to do here?
        try {
            setOldEntity(mapper.writeValueAsString(oldEntity));
            setNewEntity(mapper.writeValueAsString(newEntity));
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
    }

}
