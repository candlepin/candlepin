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

import org.fedoraproject.candlepin.audit.Event.Type;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Content;
import org.fedoraproject.candlepin.model.Entitlement;
import org.fedoraproject.candlepin.model.Product;
import org.fedoraproject.candlepin.model.ProductContent;
import org.fedoraproject.candlepin.model.Subscription;
import org.fedoraproject.candlepin.pki.PKIReader;
import org.fedoraproject.candlepin.pki.PKIUtility;
import org.fedoraproject.candlepin.util.Util;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

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
            .put(Event.Target.OWNER, new OwnerFunction())
            .build();

    private ObjectMapper mapper;
    private PKIReader reader;
    private PKIUtility pkiutil;

    @Inject
    public AMQPBusEventAdapter(ObjectMapper mapper,
        PKIReader rdr, PKIUtility util) {
        
        this.mapper = mapper;
        this.reader = rdr;
        this.pkiutil = util;
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
            Map<String, Object> envelope = Util.newMap();
            Map<String, Object> body = Util.newMap();
            envelope.put("version", "0.1"); // FIXME: how do we avoid hardcoded
            body.put("id", event.getEntityId());
            populate(event, body);
            envelope.put("event", body);
            return serialize(envelope);
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
                log.debug("Subscription message:" + subscription.getId());
                // Owner is in every type
                result.put("owner", subscription.getOwner().getKey());
                
                if (event.getType() != Event.Type.DELETED) {
                    log.debug("Subscription is NOT DELETED, which is good");
                    result.put("name", subscription.getProduct().getId());
                    result.put("entitlement_cert", subscription.getCertificate().getCert());
                    result.put("cert_public_key", subscription.getCertificate().getKey());
                    try {
                        // FIXME: wow this is crappy new String ...
                        result.put("ca_cert", new String(pkiutil.getPemEncoded(
                            reader.getUpstreamCACert())));
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    } 
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
                    contentMap.put("gpg_key_url", content.getGpgUrl());

                    contentList.add(contentMap);
                }
            }

            return contentList;
        }
        
    }

    

    /**
     * OwnerFunction
     */
    class OwnerFunction extends EventFunction implements
        Function<Event, String> {
        @Override
        protected void populate(Event event, Map<String, Object> result) {
            //does nothing for now. owner id already in there...
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
