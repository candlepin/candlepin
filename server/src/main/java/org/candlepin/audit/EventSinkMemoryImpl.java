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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.candlepin.model.Consumer;
import org.candlepin.model.Entitlement;
import org.candlepin.model.Owner;
import org.candlepin.model.Pool;
import org.candlepin.model.Rules;
import org.candlepin.model.activationkeys.ActivationKey;
import org.candlepin.model.dto.Subscription;
import org.candlepin.policy.js.compliance.ComplianceStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Singleton;

/**
 * Pushes Event objects to registered Listeners.
 */
@Singleton
public class EventSinkMemoryImpl implements EventSink {

    private static Logger log = LoggerFactory.getLogger(EventSinkMemoryImpl.class);
    private EventFactory eventFactory;
    private EventFilter eventFilter;
    private List<EventListener> listeners = new ArrayList<EventListener>();

    @Inject
    public EventSinkMemoryImpl(EventFilter eventFilter, EventFactory eventFactory) {
        this.eventFactory = eventFactory;
        this.eventFilter = eventFilter;
    }

    /**
     * Initializes the Singleton from the ContextListener not from the ctor.
     * @throws Exception thrown if there's a problem creating the session factory.
     */
    @Override
    public void initialize() throws Exception {
    }

    /**
     * Adds an event to the queue to be sent on successful completion of the request or job.
     * sendEvents() must be called for these events to actually go out. This happens
     * automatically after each successful REST API request, and KingpingJob. If either
     * is not successful, rollback() must be called.
     *
     * Events are filtered, meaning that some of them might not even get into HornetQ.
     * Details about the filtering are documented in EventFilter class
     *
     * HornetQ transaction actually manages the queue of events to be sent.
     */
    @Override
    public void queueEvent(Event event) {
        if (eventFilter.shouldFilter(event)) {
            log.debug("Filtering event {}", event);
            return;
        }

        log.debug("Queuing event: {}", event);

        try {
            for(EventListener listener: listeners) {
                listener.onEvent(event);
            }
        }
        catch (Exception e) {
            log.error("Error while trying to send event: " + event, e);
        }
    }

    /**
     * Dispatch queued events. (if there are any)
     *
     * Typically only called after a successful request or job execution.
     */
    @Override
    public void sendEvents() {
    }

    @Override
    public void rollback() {
    }



    @Override
    public List<QueueStatus> getQueueInfo() {
        // TODO Auto-generated method stub
        return null;
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

    public void emitSubscriptionExpired(Subscription subscription) {
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
    public void emitCompliance(Consumer consumer,
            Set<Entitlement> entitlements, ComplianceStatus compliance) {
        queueEvent(eventFactory.complianceCreated(consumer, entitlements, compliance));
    }

    @Override
    public void registerListener(EventListener instance) {
        listeners.add(instance);
    }
}
