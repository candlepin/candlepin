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

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.codehaus.jackson.map.ObjectMapper;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.User;
import org.fedoraproject.candlepin.service.UserServiceAdapter;
import org.fedoraproject.candlepin.util.Util;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
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
    private final ImmutableMap<Event.Target, ? extends Function<Event, String>> mp =
        new ImmutableMap.Builder<Event.Target, Function<Event, String>>()
            .put(Event.Target.CONSUMER, new ConsumerStrFunc())
            .put(Event.Target.USER, new UserStrFunc())
            .put(Event.Target.ROLE, new RoleStrFunc())
      //      .put(Event.Target.PRODUCT, new ProductStrFunc())
            .build();
    @Inject
    public AMQPBusEventAdapter(UserServiceAdapter serviceAdapter) {
        this.userServiceAdapter = serviceAdapter;
    }
    
    @Override
    public String apply(Event event) {
        Function<Event, String> func = mp.get(event.getTarget());
        if (func != null) {
            return func.apply(event);
        }
        else {
            log.warn("Unknown entity: " + event + " . Skipping serialization");
            return "";
        }
    }

    private class ProductStrFunc implements Function<Event, String> {
        @Override
        public String apply(Event event) {
            Map<String, Object> result = initializeWithEntityId(event);
            Product product = deserialize(event.getNewEntity(), Product.class);
            result.put("name", product.getName());
            //TODO: what to store in description field?
            result.put("description", "product : " + product.toString());
            //result.put("", product.)
            return serialize(result);
        }

    }

    private class ConsumerStrFunc implements Function<Event, String> {
        @Override
        public String apply(Event event) {
            Map<String, Object> result = initializeWithEntityId(event);
            //if not deleted, then it has to be either created/updated
            if (!event.getType().equals(Event.Type.DELETED)) {
                result.put("description", event.getNewEntity());
                //TODO: What should description have?
            }
            return serialize(result);
        }
    }

    private class UserStrFunc implements Function<Event, String> {
        @Override
        public String apply(Event event) {
            Map<String, Object> result = initializeWithEntityId(event);
            if (!event.getType().equals(Event.Type.DELETED)) {
                User user = deserialize(event.getNewEntity(), User.class);
                if (user != null) {
                    result.put("login", user.getUsername()); //TODO: login == name?
                    result.put("name", user.getUsername());
                    //TODO: use cache for user roles?
                    result.put("roles", userServiceAdapter.getRoles(user.getUsername()));
                }
            }
            return serialize(result);
        }
    }

    private class RoleStrFunc implements Function<Event, String> {
        @Override
        public String apply(Event event) {
          //TODO roles are static in candlpin for now..
            return event.toString();
        }
    }

    /**
     * @param result
     */
    private String serialize(Map<String, Object> result) {
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

    /**
     * @param event
     * @return
     */
    private Map<String, Object> initializeWithEntityId(Event event) {
        Map<String, Object> result = Util.newMap();
        result.put("id", event.getEntityId());
        return result;
    }

}
