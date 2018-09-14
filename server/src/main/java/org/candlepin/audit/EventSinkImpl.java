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
import org.candlepin.controller.ModeManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Rules;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.policy.SystemPurposeComplianceStatus;
import org.candlepin.policy.js.compliance.ComplianceStatus;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Singleton;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientProducer;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

/**
 * EventSink - Queues events to be sent after request/job completes, and handles actual
 * sending of events on successful job or API request, as well as rollback if either fails.
 */
@Singleton
public class EventSinkImpl implements EventSink {
    private static Logger log = LoggerFactory.getLogger(EventSinkImpl.class);
    private EventFactory eventFactory;
    private ClientSessionFactory factory;
    private Configuration config;
    private ObjectMapper mapper;
    private EventFilter eventFilter;
    private int largeMsgSize;
    private ModeManager modeManager;

    /*
     * Important use of ThreadLocal here, each Tomcat/Quartz thread gets it's own session
     * which is reused across invocations. Sessions must have commit or rollback called
     * on them per request/job. This is handled in EventFilter for the API, and KingpinJob
     * for quartz jobs.
     */
    private ThreadLocal<ClientSession> sessions = new ThreadLocal<>();
    private ThreadLocal<ClientProducer> producers = new ThreadLocal<>();

    @Inject
    public EventSinkImpl(EventFilter eventFilter, EventFactory eventFactory,
        ObjectMapper mapper, Configuration config, ModeManager modeManager) {
        this.eventFactory = eventFactory;
        this.mapper = mapper;
        this.config = config;
        this.eventFilter = eventFilter;
        this.modeManager = modeManager;
        largeMsgSize = config.getInt(ConfigProperties.ACTIVEMQ_LARGE_MSG_SIZE);
    }

    /**
     * Initializes the Singleton from the ContextListener not from the ctor.
     * @throws Exception thrown if there's a problem creating the session factory.
     */
    @Override
    public void initialize() throws Exception {
        factory = createClientSessionFactory();
    }

    protected ClientSessionFactory createClientSessionFactory() throws Exception {
        ServerLocator locator = ActiveMQClient.createServerLocatorWithoutHA(
            new TransportConfiguration(InVMConnectorFactory.class.getName()));
        locator.setMinLargeMessageSize(largeMsgSize);
        locator.setReconnectAttempts(-1);
        return locator.createSessionFactory();
    }

    protected ClientSession getClientSession() {
        ClientSession session = sessions.get();
        if (session == null || session.isClosed()) {
            try {
                /*
                 * Use a transacted ActiveMQ session, events will not be dispatched until
                 * commit() is called on it, and a call to rollback() will revert any queued
                 * messages safely and the session is then ready to start over the next time
                 * the thread is used.
                 */
                session = factory.createTransactedSession();
            }
            catch (ActiveMQException e) {
                throw new RuntimeException(e);
            }
            log.debug("Created new ActiveMQ session.");
            sessions.set(session);
        }
        return session;
    }

    protected ClientProducer getClientProducer() {
        ClientProducer producer = producers.get();
        if (producer == null) {
            try {
                producer = getClientSession().createProducer(MessageAddress.DEFAULT_EVENT_MESSAGE_ADDRESS);
            }
            catch (ActiveMQException e) {
                throw new RuntimeException(e);
            }
            log.info("Created new ActiveMQ producer.");
            producers.set(producer);
        }
        return producer;
    }

    @Override
    public List<QueueStatus> getQueueInfo() {
        List<QueueStatus> results = new LinkedList<>();
        try {

            ClientSession session = getClientSession();
            session.start();
            for (String listenerClassName : ActiveMQContextListener.getActiveMQListeners(config)) {
                String queueName = "event." + listenerClassName;
                long msgCount = session.queueQuery(new SimpleString(queueName)).getMessageCount();
                results.add(new QueueStatus(queueName, msgCount));
            }
        }
        catch (Exception e) {
            log.error("Error looking up ActiveMQ queue info: ", e);
        }
        return results;
    }

    /**
     * Adds an event to the queue to be sent on successful completion of the request or job.
     * sendEvents() must be called for these events to actually go out. This happens
     * automatically after each successful REST API request, and KingpingJob. If either
     * is not successful, rollback() must be called.
     *
     * Events are filtered, meaning that some of them might not even get into ActiveMQ.
     * Details about the filtering are documented in EventFilter class
     *
     * ActiveMQ transaction actually manages the queue of events to be sent.
     */
    @Override
    public void queueEvent(Event event) {
        if (eventFilter.shouldFilter(event)) {
            log.debug("Filtering event {}", event);
            return;
        }

        modeManager.throwRestEasyExceptionIfInSuspendMode();
        log.debug("Queuing event: {}", event);

        try {
            ClientSession session = getClientSession();
            ClientMessage message = session.createMessage(true);
            String eventString = mapper.writeValueAsString(event);
            message.getBodyBuffer().writeString(eventString);

            // NOTE: not actually send until we commit the session.
            getClientProducer().send(message);
        }
        catch (Exception e) {
            log.error("Error while trying to send event: {}", e);
        }
    }

    /**
     * Dispatch queued events. (if there are any)
     *
     * Typically only called after a successful request or job execution.
     */
    @Override
    public void sendEvents() {
        try {
            log.debug("Committing ActiveMQ transaction.");
            getClientSession().commit();
        }
        catch (Exception e) {
            // This would be pretty bad, but we always try not to let event errors
            // interfere with the operation of the overall application.
            log.error("Error committing ActiveMQ transaction", e);
        }
    }

    @Override
    public void rollback() {
        log.warn("Rolling back ActiveMQ transaction.");
        try {
            ClientSession session = getClientSession();
            session.rollback();
        }
        catch (ActiveMQException e) {
            log.error("Error rolling back ActiveMQ transaction", e);
        }
    }

    public void emitConsumerCreated(Consumer newConsumer) {
        Event e = eventFactory.consumerCreated(newConsumer);
        queueEvent(e);
    }

    public void emitOwnerCreated(Owner newOwner) {
        Event e = eventFactory.ownerCreated(newOwner);
        queueEvent(e);
    }

    public void emitOwnerMigrated(Owner owner) {
        Event e = eventFactory.ownerModified(owner);
        queueEvent(e);
    }

    public void emitPoolCreated(Pool newPool) {
        Event e = eventFactory.poolCreated(newPool);
        queueEvent(e);
    }

    public void emitExportCreated(Consumer consumer) {
        Event e = eventFactory.exportCreated(consumer);
        queueEvent(e);
    }

    public void emitImportCreated(Owner owner) {
        Event e = eventFactory.importCreated(owner);
        queueEvent(e);
    }

    public void emitActivationKeyCreated(ActivationKey key) {
        Event e = eventFactory.activationKeyCreated(key);
        queueEvent(e);
    }

    public void emitSubscriptionExpired(SubscriptionDTO subscription) {
        Event e = eventFactory.subscriptionExpired(subscription);
        queueEvent(e);
    }

    @Override
    public void emitRulesModified(Rules oldRules, Rules newRules) {
        queueEvent(eventFactory.rulesUpdated(oldRules, newRules));
    }

    @Override
    public void emitRulesDeleted(Rules rules) {
        queueEvent(eventFactory.rulesDeleted(rules));
    }

    @Override
    public void emitCompliance(Consumer consumer, ComplianceStatus compliance) {
        queueEvent(eventFactory.complianceCreated(consumer, compliance));
    }

    @Override
    public void emitCompliance(Consumer consumer, SystemPurposeComplianceStatus compliance) {
        queueEvent(eventFactory.complianceCreated(consumer, compliance));
    }
}
