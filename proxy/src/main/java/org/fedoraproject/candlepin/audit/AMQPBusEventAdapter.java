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
import org.fedoraproject.candlepin.model.User;
import org.fedoraproject.candlepin.service.UserServiceAdapter;
import org.fedoraproject.candlepin.util.Util;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.fedoraproject.candlepin.model.Subscription;

/**
 * @author ajay
 */
@Singleton
public class AMQPBusEventAdapter implements Function<Event, String> {

    private static Log log = LogFactory.getLog(AMQPBusEventAdapter.class);

    private final ImmutableMap<Event.Target, ? extends Function<Event, String>> mp =
        new ImmutableMap.Builder<Event.Target, Function<Event, String>>()
            .put(Event.Target.CONSUMER, new ConsumerFunction())
            .put(Event.Target.SUBSCRIPTION, new SubscriptionFunction())
            .build();

    private Config config;
    private ObjectMapper mapper;

    @Inject
    public AMQPBusEventAdapter(Config config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
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

    private abstract class EventFunction implements Function<Event, String> {

        @Override
        public String apply(Event event) {
            Map<String, Object> result = Util.newMap();
            result.put("id", event.getEntityId());

            populate(event, result);
            return serialize(result);
        }

        protected abstract void populate(Event event, Map<String, Object> result);
    }

    private class ConsumerFunction extends EventFunction {

        @Override
        protected void populate(Event event, Map<String, Object> result) {
            if (event.getType() != Event.Type.DELETED) {
                result.put("description", event.getNewEntity());
                //TODO: What should description have?
            }
        }
    }

    private class SubscriptionFunction extends EventFunction {

        @Override
        protected void populate(Event event, Map<String, Object> result) {
            Subscription subscription = deserialize(event.getNewEntity(),
                    Subscription.class);

            if (subscription != null) {
                result.put("owner", subscription.getOwner().getKey());
                
                if (event.getType() != Event.Type.DELETED) {
                    result.put("name", subscription.getProduct().getId());

                    // no idea what this should be
                    result.put("description", event.getNewEntity());
                    result.put("ca_cert", config.getString(
                            ConfigProperties.CA_CERT_UPSTREAM));

                }
            }
        }
        
    }

    /**
     * @param result
     */
    private String serialize(Map<String, Object> result) {
        try {
            return this.mapper.writeValueAsString(result);
        }
        catch (Exception e) {
            log.warn("Unable to serialize :", e);
        }
        return "";
    }
    
    private <T> T deserialize(String value, Class<T> clas) {
        try {
            return this.mapper.readValue(value, clas);
        }
        catch (Exception e) {
            log.warn("Unable to de-serialize :", e);
        }
        return null;
    }

}
