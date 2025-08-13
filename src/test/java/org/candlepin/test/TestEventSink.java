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
package org.candlepin.test;

import org.candlepin.audit.ActiveMQContextListener;
import org.candlepin.audit.Event;
import org.candlepin.audit.EventFactory;
import org.candlepin.audit.EventFilter;
import org.candlepin.audit.EventSink;
import org.candlepin.config.Configuration;
import org.candlepin.controller.mode.CandlepinModeManager;
import org.candlepin.controller.mode.CandlepinModeManager.Mode;
import org.candlepin.dto.api.server.v1.QueueStatus;
import org.candlepin.dto.manifest.v1.SubscriptionDTO;
import org.candlepin.model.Consumer;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Rules;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.policy.SystemPurposeComplianceStatus;
import org.candlepin.policy.js.compliance.ComplianceStatus;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.inject.Inject;



/**
 * The TestEventSink class provides an event sink implementation that captures events that are successfully
 * dispatched for testing purposes without needing to rely on mocks that may or may not fully capture the
 * eventing framework and configuration.
 */
public class TestEventSink implements EventSink {
    private static Logger log = LoggerFactory.getLogger(TestEventSink.class);

    private final Configuration config;
    private final EventFactory eventFactory;
    private final EventFilter eventFilter;
    private final CandlepinModeManager modeManager;

    private int dispatchedEventCount;
    private final Queue<Event> dispatchedEvents;
    private final Queue<Event> eventQueue;

    @Inject
    public TestEventSink(Configuration config, EventFilter eventFilter, CandlepinModeManager modeManager,
        EventFactory eventFactory) {

        this.config = Objects.requireNonNull(config);
        this.eventFilter = Objects.requireNonNull(eventFilter);
        this.eventFactory = Objects.requireNonNull(eventFactory);
        this.modeManager = Objects.requireNonNull(modeManager);

        this.dispatchedEvents = new ConcurrentLinkedQueue<>();
        this.eventQueue = new ConcurrentLinkedQueue<>();
    }

    @Override
    public void queueEvent(Event event) {
        // This is technically not accurate to the base EventSinkImpl, as it doesn't test for nulls, but we
        // should fix that rather than ignore it and hope for the best.
        if (event == null) {
            throw new IllegalArgumentException("event is null");
        }

        if (this.eventFilter.shouldFilter(event)) {
            log.debug("Filtering event {}", event);
            return;
        }

        if (this.modeManager.getCurrentMode() != Mode.NORMAL) {
            throw new IllegalStateException("Candlepin is in suspend mode");
        }

        this.eventQueue.add(event);
    }

    @Override
    public void sendEvents() {
        this.dispatchedEventCount += this.eventQueue.size();
        this.dispatchedEvents.addAll(this.eventQueue);
        this.eventQueue.clear();
    }

    @Override
    public void rollback() {
        this.eventQueue.clear();
    }

    @Override
    public List<QueueStatus> getQueueInfo() {
        return ActiveMQContextListener.getActiveMQListeners(this.config)
            .stream()
            .map(listenerClassName -> "event." + listenerClassName)
            .map(queueName -> new QueueStatus().queueName(queueName).pendingMessageCount(0L))
            .toList();
    }

    /**
     * Fetches the total number of events dispatched by this event sink; including those that have already
     * been processed and removed from the dispatched event queue. Events which were queued but rolled back
     * are not included in this count.
     *
     * @return
     *  the number of events dispatched by this event sink
     */
    public int getDispatchedEventCount() {
        return this.dispatchedEventCount;
    }

    /**
     * Fetches a queue containing the events that were sent (or dispatched) after being queued. This queue
     * will represent events that went through the flow of being queued via <code>queueEvent(...)</code> and
     * then dispatched via successful call to <code>sendEvents()</code>. Events which were queued in the
     * event sink but rolled back are not included in this queue.
     *
     * @return
     *  a queue containing the events dispatched by this event sink
     */
    public Queue<Event> getDispatchedEvents() {
        return new LinkedList<>(this.dispatchedEvents);
    }

    /**
     * Fetches a queue containing the events that are currently in queue, but not yet dispatched. This queue
     * will represent events that have been received via <code>queueEvent(...)</code>, but have not yet been
     * commited and dispatched during a call to <code>sendEvents()</code>.
     *
     * @return
     *  a queue containing the events queued in this event sink
     */
    public Queue<Event> getQueuedEvents() {
        return new LinkedList<>(this.eventQueue);
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////

    // EventSink utility function overrides
    // These should all be one-liners that pass through to the event factory and then queue the event.

    @Override
    public void emitConsumerCreated(Consumer consumer) {
        this.queueEvent(this.eventFactory.consumerCreated(consumer));
    }

    @Override
    public void emitOwnerCreated(Owner owner) {
        this.queueEvent(this.eventFactory.ownerCreated(owner));
    }

    @Override
    public void emitOwnerMigrated(Owner owner) {
        this.queueEvent(this.eventFactory.ownerModified(owner));
    }

    @Override
    public void emitPoolCreated(Pool pool) {
        this.queueEvent(this.eventFactory.poolCreated(pool));
    }

    @Override
    public void emitExportCreated(Consumer consumer) {
        this.queueEvent(this.eventFactory.exportCreated(consumer));
    }

    @Override
    public void emitImportCreated(Owner owner) {
        this.queueEvent(this.eventFactory.importCreated(owner));
    }

    @Override
    public void emitActivationKeyCreated(ActivationKey key) {
        this.queueEvent(this.eventFactory.activationKeyCreated(key));
    }

    @Override
    public void emitSubscriptionExpired(SubscriptionDTO subscription) {
        this.queueEvent(this.eventFactory.subscriptionExpired(subscription));
    }

    @Override
    public void emitRulesModified(Rules oldRules, Rules newRules) {
        this.queueEvent(this.eventFactory.rulesUpdated(oldRules, newRules));
    }

    @Override
    public void emitRulesDeleted(Rules rules) {
        this.queueEvent(this.eventFactory.rulesDeleted(rules));
    }

    @Override
    public void emitCompliance(Consumer consumer, ComplianceStatus compliance) {
        this.queueEvent(this.eventFactory.complianceCreated(consumer, compliance));
    }

    @Override
    public void emitCompliance(Consumer consumer, SystemPurposeComplianceStatus compliance) {
        this.queueEvent(this.eventFactory.complianceCreated(consumer, compliance));
    }

    @Override
    public void emitOwnerContentAccessModeChanged(Owner owner) {
        this.queueEvent(this.eventFactory.ownerContentAccessModeChanged(owner));
    }

}
