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

import java.util.LinkedList;
import java.util.List;
import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Rules;
import org.candlepin.model.Subscription;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import com.google.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.Set;

/**
 * EventSink - Reliably dispatches events to all configured listeners.
 */
public class EventSinkImpl implements EventSink {

    private static Logger log = LoggerFactory.getLogger(EventSinkImpl.class);
    private EventFactory eventFactory;
    private HornetqEventDispatcher dispatcher;

    // Hold onto events we will send on successful completion of request/job:
    private List<Event> eventQueue;

    @Inject
    public EventSinkImpl(EventFactory eventFactory, HornetqEventDispatcher dispatcher) {
        this.eventFactory = eventFactory;
        this.dispatcher = dispatcher;
        this.eventQueue = new LinkedList<Event>();
    }

    private List<Event> getEventQueue() {
        return eventQueue;
    }

    /**
     * Adds an event to the queue. Event will not be sent until sendEvents is called,
     * typically after a successful request or job execution.
     */
    @Override
    public void queueEvent(Event event) {
        if (log.isDebugEnabled()) {
            log.debug("Queuing event: " + event);
        }
        getEventQueue().add(event);
    }

    /**
     * Dispatch all queued events. Typically only called after a successful request or
     * job execution.
     */
    @Override
    public void sendEvents() {
        log.info("Dispatching " + getEventQueue().size() + " events");
        for (Event e : getEventQueue()) {
            dispatcher.sendEvent(e);
        }
        getEventQueue().clear();
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
        Event e = eventFactory.ownerMigrated(owner);
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

    @Override
    public void emitSubscriptionCreated(Subscription subscription) {
        Event e = eventFactory.subscriptionCreated(subscription);
        queueEvent(e);
    }

    public void emitSubscriptionModified(Subscription old, Subscription newSub) {
        queueEvent(eventFactory.subscriptionModified(old, newSub));
    }

    public Event createSubscriptionDeleted(Subscription todelete) {
        return eventFactory.subscriptionDeleted(todelete);
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
    public void emitCompliance(Consumer consumer,
            Set<Entitlement> entitlements, ComplianceStatus compliance) {
        queueEvent(eventFactory.complianceCreated(consumer, entitlements, compliance));
    }
}
