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
import org.fedoraproject.candlepin.config.Config;
import org.fedoraproject.candlepin.config.ConfigProperties;
import org.fedoraproject.candlepin.model.Consumer;
import org.fedoraproject.candlepin.model.Owner;
import org.fedoraproject.candlepin.model.Pool;
import org.fedoraproject.candlepin.model.Subscription;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;

import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * EventSink - Reliably dispatches events to all configured listeners.
 */
@Singleton
public class EventSinkImpl implements EventSink {
    
    private static Logger log = Logger.getLogger(EventSinkImpl.class);
    private EventFactory eventFactory;
    private ClientSessionFactory factory;
    private ClientSession clientSession;
    private ClientProducer clientProducer;
    private int largeMsgSize;
    private ObjectMapper mapper;

    @Inject
    public EventSinkImpl(EventFactory eventFactory, ObjectMapper mapper) {
        this.eventFactory = eventFactory;
        this.mapper = mapper;
        factory =  createClientSessionFactory();

        largeMsgSize = new Config().getInt(ConfigProperties.HORNETQ_LARGE_MSG_SIZE);
        factory.setMinLargeMessageSize(largeMsgSize);
        try {
            clientSession = factory.createSession();
            clientProducer = clientSession.createProducer(EventSource.QUEUE_ADDRESS);
        }
        catch (HornetQException e) {
            throw new RuntimeException(e);
        }
    }

    protected ClientSessionFactory createClientSessionFactory() {
        return HornetQClient.createClientSessionFactory(
            new TransportConfiguration(InVMConnectorFactory.class.getName()));
    }

    @Override
    public void sendEvent(Event event) {
        if (log.isDebugEnabled()) {
            log.debug("Sending event - " + event);
        }
        try {
            ClientMessage message = clientSession.createMessage(true);
            String eventString = mapper.writeValueAsString(event);
            message.getBodyBuffer().writeString(eventString);
            clientProducer.send(message);
        }
        catch (Exception e) {
            log.error("Error while trying to send event: " + event, e);
        }
    }
    
    public void emitConsumerCreated(Consumer newConsumer) {
        Event e = eventFactory.consumerCreated(newConsumer);
        sendEvent(e);
    }

    public void emitOwnerCreated(Owner newOwner) {
        Event e = eventFactory.ownerCreated(newOwner);
        sendEvent(e);
    }

    public void emitOwnerMigrated(Owner owner) {
        Event e = eventFactory.ownerMigrated(owner);
        sendEvent(e);
    }
    
    public void emitPoolCreated(Pool newPool) {
        Event e = eventFactory.poolCreated(newPool);
        sendEvent(e);
    }
    
    public void emitExportCreated(Consumer consumer) {
        Event e = eventFactory.exportCreated(consumer);
        sendEvent(e);
    }
    
    public void emitImportCreated(Owner owner) {
        Event e = eventFactory.importCreated(owner);
        sendEvent(e);
    }

    @Override
    public void emitSubscriptionCreated(Subscription subscription) {
        Event e = eventFactory.subscriptionCreated(subscription);
        sendEvent(e);
    }

    public void emitSubscriptionModified(Subscription old, Subscription newSub) {
        sendEvent(eventFactory.subscriptionModified(old, newSub));
    }

    public Event createSubscriptionDeleted(Subscription todelete) {
        return eventFactory.subscriptionDeleted(todelete);
    }
}
