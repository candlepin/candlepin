/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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
package org.candlepin.audit;

import org.candlepin.config.Config;
import org.candlepin.config.ConfigProperties;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Rules;
import org.candlepin.model.Subscription;
import org.candlepin.model.activationkeys.ActivationKey;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.ServerLocator;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * EventSink - Reliably dispatches events to all configured listeners.
 */
@Singleton
public class EventSinkImpl implements EventSink {

    private static Logger log = LoggerFactory.getLogger(EventSinkImpl.class);
    private EventFactory eventFactory;
    private ClientSessionFactory factory;
    private int largeMsgSize;
    private ObjectMapper mapper;
    private ThreadLocal<ClientSession> sessions = new ThreadLocal<ClientSession>();
    private ThreadLocal<ClientProducer> producers = new ThreadLocal<ClientProducer>();

    @Inject
    public EventSinkImpl(EventFactory eventFactory, ObjectMapper mapper, Config config) {
        this.eventFactory = eventFactory;
        this.mapper = mapper;
        try {
            largeMsgSize = config.getInt(ConfigProperties.HORNETQ_LARGE_MSG_SIZE);

            factory =  createClientSessionFactory();
        }
        catch (HornetQException e) {
            throw new RuntimeException(e);
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected ClientSessionFactory createClientSessionFactory() throws Exception {
        ServerLocator locator = HornetQClient.createServerLocatorWithoutHA(
            new TransportConfiguration(InVMConnectorFactory.class.getName()));
        locator.setMinLargeMessageSize(largeMsgSize);
        return locator.createSessionFactory();
    }

    protected ClientSession getClientSession() {
        ClientSession session = sessions.get();
        if (session == null) {
            try {
                session = factory.createSession();
            }
            catch (HornetQException e) {
                throw new RuntimeException(e);
            }
            sessions.set(session);
        }
        return session;
    }

    protected ClientProducer getClientProducer() {
        ClientProducer producer = producers.get();
        if (producer == null) {
            try {
                producer = getClientSession().createProducer(EventSource.QUEUE_ADDRESS);
            }
            catch (HornetQException e) {
                throw new RuntimeException(e);
            }
            producers.set(producer);
        }
        return producer;
    }

    @Override
    public void sendEvent(Event event) {
        if (log.isDebugEnabled()) {
            log.debug("Sending event - " + event);
        }
        try {
            ClientMessage message = getClientSession().createMessage(true);
            String eventString = mapper.writeValueAsString(event);
            message.getBodyBuffer().writeString(eventString);
            getClientProducer().send(message);
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

    public void emitActivationKeyCreated(ActivationKey key) {
        Event e = eventFactory.activationKeyCreated(key);
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

    @Override
    public void emitRulesModified(Rules oldRules, Rules newRules) {
        sendEvent(eventFactory.rulesUpdated(oldRules, newRules));
    }

    @Override
    public void emitRulesDeleted(Rules rules) {
        sendEvent(eventFactory.rulesDeleted(rules));
    }
}
