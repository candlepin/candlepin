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

import org.apache.log4j.Logger;
import org.codehaus.jackson.map.ObjectMapper;
import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.model.Consumer;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;

import com.google.inject.Inject;

/**
 * EventSink - Reliably dispatches events to all configured listeners.
 */
public class EventSink {
    
    private static Logger log = Logger.getLogger(EventSink.class);
    private EventFactory eventFactory;

    @Inject
    public EventSink(EventFactory eventFactory) {
        this.eventFactory = eventFactory;
    }
    
    public static void sendEvent(Event event) {
        if (log.isDebugEnabled()) {
            log.debug("Sending event - " + event);
        }
        
        try {
            ClientSessionFactory factory =  HornetQClient.createClientSessionFactory(
                new TransportConfiguration(InVMConnectorFactory.class.getName()));
            
            ClientSession session = factory.createSession();
            
            ClientProducer producer = session.createProducer(EventSource.QUEUE_ADDRESS);
            
            ClientMessage message = session.createMessage(true);
            
            ObjectMapper mapper = new ObjectMapper();
            String eventString = mapper.writeValueAsString(event);
            message.getBodyBuffer().writeString(eventString);
            
            producer.send(message);
            session.close();
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
    public void emitConsumerCreated(Principal principal, Consumer newConsumer) {
        Event e = eventFactory.consumerCreated(principal, newConsumer);
        EventSink.sendEvent(e);
    }

}
