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

import org.codehaus.jackson.map.ObjectMapper;
import org.fedoraproject.candlepin.audit.Event.Type;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.util.Util;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.fedoraproject.candlepin.model.Content;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductContent;
import org.fedoraproject.candlepin.model.Subscription;

/**
 */
@Singleton
public class AMQPBusEventAdapter implements Function<Event, String> {

    private static Logger log = LoggerFactory.getLogger(AMQPBusEventAdapter.class);

    private final ImmutableMap<Event.Target, ? extends Function<Event, String>> mp =
        new ImmutableMap.Builder<Event.Target, Function<Event, String>>()
            .put(Event.Target.CONSUMER, new ConsumerFunction())
            .put(Event.Target.SUBSCRIPTION, new SubscriptionFunction())
            .put(Event.Target.ENTITLEMENT, new EntitlementFunction())
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
            String result = func.apply(event);
            log.debug("AMQPBusEventAdapter.apply(event) = {}", result);
            return result;
        }
        else {
            log.warn("Unknown entity: {}. Skipping serialization", event);
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
            Consumer consumer = deserialize(event, Consumer.class);
            if (consumer != null) {
                result.put("id", consumer.getUuid());
                result.put("owner", event.getOwnerId());
                switch (event.getType()) {
                    case CREATED:
                        consumerCreated(consumer, result, event);
                        break;
                    case DELETED:
                        consumerDeleted(consumer, result, event);
                        break;
                    case MODIFIED:
                        consumerModified(consumer, result, event);
                        break;
                    default:
                        log.debug("Unknown eventType: " + event.getType());
                }
            }
        }
        
        private void consumerModified(Consumer consumer,
            Map<String, Object> result, Event event) {
            consumerCreated(consumer, result, event);
        }

        private void consumerDeleted(Consumer consumer,
            Map<String, Object> rs, Event event) {
            //no extra attributes to take care of... but maybe in future.
        }

        private void consumerCreated(Consumer consumer,
            Map<String, Object> rs, Event event) {
            rs.put("identity_cert", consumer.getIdCert().getCert());
            rs.put("identity_cert_key", consumer.getIdCert().getKey());
            rs.put("hardware_facts", consumer.getFacts());            
        }
    }
    

    /**
     * EntitlementStrFunc
     */
    private class EntitlementFunction extends EventFunction {

        @Override
        protected void populate(Event event, Map<String, Object> result) {
            Entitlement entitlement = deserialize(event, Entitlement.class);
            if (event.getType() == Type.CREATED ||
                event.getType() == Type.DELETED) {
                Consumer consumer = entitlement.getConsumer();
                result.put("id", consumer.getUuid());
                result.put("owner", event.getOwnerId());
                result.put("consumer_os_arch", consumer.getFact("uname.machine"));
                result.put("consumer_os_version", consumer.getFact("distribution.version"));
                result.put("product_id", entitlement.getProductId());
            }
        }

    }
    private class SubscriptionFunction extends EventFunction {

        @Override
        protected void populate(Event event, Map<String, Object> result) {
            Subscription subscription = deserialize(event.getNewEntity(),
                    Subscription.class);

            if (subscription != null) {
                // Owner is in every type
                result.put("owner", subscription.getOwner().getKey());
                
                if (event.getType() != Event.Type.DELETED) {
                    result.put("name", subscription.getProduct().getId());
                    result.put("entitlement_cert", subscription.getCertificate().getCert());
                    result.put("cert_public_key", subscription.getCertificate().getKey());
                    result.put("ca_cert", config.getString(
                            ConfigProperties.CA_CERT_UPSTREAM));
                    result.put("content_sets", createContentMap(subscription));
                }
            }
        }

        private List<Map<String, String>> createContentMap(Subscription sub) {
            List<Map<String, String>> contentList = new LinkedList<Map<String, String>>();

            for (Product product : sub.getProvidedProducts()) {
                for (ProductContent prodContent : product.getProductContent()) {
                    Content content = prodContent.getContent();

                    Map<String, String> contentMap = new HashMap<String, String>();
                    contentMap.put("content_set_label", content.getLabel());
                    contentMap.put("content_rel_url", content.getContentUrl());

                    contentList.add(contentMap);
                }
            }

            return contentList;
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
    
    private <T> T deserialize(Event event, Class<T> clas) {
        return deserialize(event.getType() == Type.DELETED ? event
            .getOldEntity() : event.getNewEntity(), clas);
    }

}
