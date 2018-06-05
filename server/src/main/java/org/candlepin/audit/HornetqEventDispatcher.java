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

import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.SimpleString;
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

import java.util.LinkedList;
import java.util.List;

import javax.inject.Singleton;

/**
 * HornetqEventDispatcher - Singleton responsible for managing hornetq sessions and
 * producers and dispatching events to all configured listeners.
 */
@Singleton
public class HornetqEventDispatcher  {

    private static Logger log = LoggerFactory.getLogger(HornetqEventDispatcher.class);
    private ClientSessionFactory factory;
    private Configuration config;
    private ObjectMapper mapper;
    private int largeMsgSize;
    private ThreadLocal<ClientSession> sessions = new ThreadLocal<ClientSession>();
    private ThreadLocal<ClientProducer> producers = new ThreadLocal<ClientProducer>();

    @Inject
    public HornetqEventDispatcher(ObjectMapper mapper, Configuration config) {
        this.mapper = mapper;
        this.config = config;
        largeMsgSize = config.getInt(ConfigProperties.HORNETQ_LARGE_MSG_SIZE);
    }

    /**
     * Initializes the Singleton from the ContextListener not from the ctor.
     * @throws Exception thrown if there's a problem creating the session factory.
     */
    public void initialize() throws Exception {
        factory =  createClientSessionFactory();
    }

    protected ClientSessionFactory createClientSessionFactory() throws Exception {
        ServerLocator locator = HornetQClient.createServerLocatorWithoutHA(
            new TransportConfiguration(InVMConnectorFactory.class.getName()));
        locator.setMinLargeMessageSize(largeMsgSize);
        locator.setReconnectAttempts(-1);
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
            log.info("Created new HornetQ session.");
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
            log.info("Created new HornetQ producer.");
            producers.set(producer);
        }
        return producer;
    }

    public void sendEvent(Event event) {
        log.info("Sending event: {}", event);
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

    public List<QueueStatus> getQueueInfo() {
        List<QueueStatus> results = new LinkedList<QueueStatus>();
        try {

            ClientSession session = getClientSession();
            session.start();
            for (String listenerClassName : HornetqContextListener.getHornetqListeners(
                    config)) {
                String queueName = "event." + listenerClassName;
                long msgCount = session.queueQuery(new SimpleString(queueName))
                        .getMessageCount();
                results.add(new QueueStatus(queueName, msgCount));
            }
        }
        catch (Exception e) {
            log.error("Error looking up hornetq queue info: ", e);
        }
        return results;
    }

}
