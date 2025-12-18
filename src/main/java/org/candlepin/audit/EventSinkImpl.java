/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

import org.candlepin.config.Configuration;
import org.candlepin.controller.mode.CandlepinModeManager;
import org.candlepin.controller.mode.CandlepinModeManager.Mode;
import org.candlepin.dto.api.server.v1.QueueStatus;
import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.guice.CandlepinRequestScoped;
import org.candlepin.messaging.CPMException;
import org.candlepin.messaging.CPMMessage;
import org.candlepin.messaging.CPMProducer;
import org.candlepin.messaging.CPMProducerConfig;
import org.candlepin.messaging.CPMSession;
import org.candlepin.messaging.CPMSessionManager;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Rules;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.policy.SystemPurposeComplianceStatus;
import org.candlepin.policy.js.compliance.ComplianceStatus;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;

/**
 * EventSink - Queues events to be sent after request/job completes, and handles actual
 * sending of events on successful job or API request, as well as rollback if either fails.
 *
 * An single instance of this object will be created per request/job.
 */
@CandlepinRequestScoped
public class EventSinkImpl implements EventSink {
    private static final Logger log = LoggerFactory.getLogger(EventSinkImpl.class);

    public static final String EVENT_TYPE_KEY = "EVENT_TYPE";
    public static final String EVENT_TARGET_KEY = "EVENT_TARGET";

    private EventFactory eventFactory;
    private ObjectMapper mapper;
    private EventFilter eventFilter;
    private CandlepinModeManager modeManager;
    private Configuration config;

    private CPMSessionManager sessionManager;
    private EventMessageSender messageSender;

    @Inject
    public EventSinkImpl(EventFilter eventFilter, EventFactory eventFactory,
        ObjectMapper mapper, Configuration config, CPMSessionManager sessionManager,
        CandlepinModeManager modeManager) {

        this.eventFactory = eventFactory;
        this.mapper = mapper;
        this.eventFilter = eventFilter;
        this.modeManager = modeManager;
        this.config = config;
        this.sessionManager = sessionManager;
    }

    // FIXME This method really does not belong here. It should probably be moved
    //       to its own class.
    @Override
    public List<QueueStatus> getQueueInfo() {
        List<QueueStatus> results = new LinkedList<>();

        // TODO: Need to handle this

        // try (CPMSession session = this.sessionManager.createSession(false)) {
        //     session.start();
        //     for (String listenerClassName : ActiveMQContextListener.getActiveMQListeners(config)) {
        //         String queueName = "event." + listenerClassName;
        //         long msgCount = session.queueQuery(SimpleString.of(queueName)).getMessageCount();
        //         results.add(new QueueStatus()
        //             .queueName(queueName)
        //             .pendingMessageCount(msgCount)
        //         );
        //     }
        // }
        // catch (Exception e) {
        //     log.error("Error looking up ActiveMQ queue info: ", e);
        // }

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

        if (this.modeManager.getCurrentMode() != Mode.NORMAL) {
            throw new IllegalStateException("Candlepin is in suspend mode");
        }

        log.debug("Queuing event: {}", event);

        try {
            // Lazily initialize the message sender when the first
            // message gets queued.
            if (messageSender == null) {
                messageSender = new EventMessageSender(this.sessionManager);
            }

            messageSender.queueMessage(mapper.writeValueAsString(event), event.getType(), event.getTarget());
        }
        catch (Exception e) {
            log.error("Error while trying to send event", e);
        }
    }

    /**
     * Dispatch queued events. (if there are any)
     *
     * Typically only called after a successful request or job execution.
     */
    @Override
    public void sendEvents() {
        if (!hasQueuedMessages()) {
            log.debug("No events to send.");
            return;
        }
        messageSender.sendMessages();
    }

    @Override
    public void rollback() {
        if (!hasQueuedMessages()) {
            log.debug("No events to roll back.");
            return;
        }
        messageSender.cancelMessages();
    }

    private boolean hasQueuedMessages() {
        return messageSender != null;
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

    @Override
    public void emitOwnerContentAccessModeChanged(Owner owner) {
        queueEvent(eventFactory.ownerContentAccessModeChanged(owner));
    }

    /**
     * An internal class responsible for encapsulating a single session to the
     * event message broker.
     */
    private class EventMessageSender {

        private CPMSessionManager sessionManager;
        private CPMSession session;
        private CPMProducer producer;

        public EventMessageSender(CPMSessionManager sessionManager) {
            try {
                /*
                 * Uses a transacted session, events will not be dispatched until
                 * commit() is called on it, and a call to rollback() will revert any queued
                 * messages safely and the session is then ready to start over the next time
                 * the thread is used.
                 */
                this.sessionManager = sessionManager;
                this.session = this.sessionManager.createSession(true);

                CPMProducerConfig config = new CPMProducerConfig()
                    .setTransactional(true);

                this.producer = this.session.createProducer(config);
            }
            catch (Exception e) {
                throw new RuntimeException(e);
            }
            log.debug("Created new message sender.");
        }

        public void queueMessage(String eventString, Event.Type type, Event.Target target)
            throws CPMException {
            if (session.isClosed()) {
                throw new IllegalStateException("Session is closed");
            }

            CPMMessage message = session.createMessage()
                .setBody(eventString);

            // Set the event type and target if provided
            if (type != null) {
                message.setProperty(EVENT_TYPE_KEY, type.name());
            }

            if (target != null) {
                message.setProperty(EVENT_TARGET_KEY, target.name());
            }

            // NOTE: not actually sent until we commit the session.
            producer.send(MessageAddress.DEFAULT_EVENT_MESSAGE_ADDRESS, message);
        }

        public void sendMessages() {
            log.debug("Committing transaction.");
            if (!session.isClosed()) {
                try (CPMSession toClose = session) {
                    toClose.commit();
                }
                catch (CPMException e) {
                    // This would be pretty bad, but we always try not to let event errors
                    // interfere with the operation of the overall application.
                    log.error("Error committing transaction", e);
                }
            }
        }

        public void cancelMessages() {
            log.warn("Rolling back transaction.");
            if (!session.isClosed()) {
                try (CPMSession toClose = session) {
                    toClose.rollback();
                }
                catch (CPMException e) {
                    log.error("Error rolling back transaction", e);
                }
            }
        }

    }
}
