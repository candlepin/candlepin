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

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.fedoraproject.candlepin.model.User;
import org.fedoraproject.candlepin.resteasy.JsonProvider;
import org.fedoraproject.candlepin.service.UserServiceAdapter;

import com.google.common.base.Function;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * @author ajay
 */
@Singleton
public class AMQPBusEventAdapter implements Function<Event, String> {

    private ObjectMapper om = new ObjectMapper();
    private UserServiceAdapter userServiceAdapter;
    private static Log log = LogFactory.getLog(AMQPBusEventAdapter.class);
    
    @Inject
    public AMQPBusEventAdapter(UserServiceAdapter serviceAdapter) {
        this.userServiceAdapter = serviceAdapter;
        JsonProvider.configureObjectMapper(om);
    }
    
    @Override
    public String apply(Event event) {
        switch (event.getTarget()) {
            case CONSUMER:
                return consumerToKalpanaFmt(event);
            case USER:
                return usrToKalapanFmt(event);
            case ROLE:
                return roleEventToKalpanaFormat(event);
            default:
                log.warn("Unknown entity: " + event + " . Skipping serialization");
                return "";
        }
    }

    protected final String consumerToKalpanaFmt(Event event) {
        Map<Object, Object> result = initializeWithEntityId(event);
        //if not deleted, then it has to be either created/updated
        if (!event.getType().equals(Event.Type.DELETED)) {
            result.put("description", event.getNewEntity());
            //TODO: What should description have? 
        }
        return serialize(result);
    }

    /**
     * @param result
     */
    private String serialize(Map<Object, Object> result) {
        try {
            return this.om.writeValueAsString(result);
        }
        catch (Exception e) {
            log.warn("Unable to serialize :", e);
        }
        return "";
    }
    
    private <T> T deserialize(String value, Class<T> clas) {
        try {
            return om.readValue(value, clas);
        }
        catch (Exception e) {
            log.warn("Unable to de-serialize :", e);
        }
        return null;
    }

    protected final String usrToKalapanFmt(Event event) {
        Map<Object, Object> result = initializeWithEntityId(event);
        if (!event.getType().equals(Event.Type.DELETED)) {
            User user = deserialize(event.getNewEntity(), User.class);
            if (user != null) {
                result.put("login", user.getUsername()); //TODO: login == name?
                result.put("name", user.getUsername());
                result.put("roles", this.userServiceAdapter.getRoles(user.getUsername()));
            }
        }
        return serialize(result);
    }

    /**
     * @param event
     * @return
     */
    private Map<Object, Object> initializeWithEntityId(Event event) {
        Map<Object, Object> result = new HashMap<Object, Object>();
        result.put("id", event.getEntityId());
        return result;
    }

    protected final String roleEventToKalpanaFormat(Event event) {
        //TODO roles are static in candlpin for now.. 
        return event.toString(); 
    }

}
