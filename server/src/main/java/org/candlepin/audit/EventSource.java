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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;

import com.google.inject.Singleton;
import org.candlepin.controller.ActiveMQQueueHealthListener;
import org.candlepin.controller.ActiveMQStatusListener;
import org.candlepin.controller.QpidStatusListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

/**
 * EventSource
 */
@Singleton
public class EventSource implements QpidStatusListener, ActiveMQStatusListener,
        ActiveMQQueueHealthListener {
    private static Logger log = LoggerFactory.getLogger(EventSource.class);

    private ObjectMapper mapper;
    private EventSourceConnection connection;
    private List<MessageReceiver> messageReceivers = new LinkedList<>();

    @Inject
    public EventSource(EventSourceConnection connection, ObjectMapper mapper) {
        this.connection = connection;
        this.mapper = mapper;
    }

    protected void shutDown() {
        closeEventReceivers();
        this.connection.close();
    }

    void registerListener(EventListener listener) throws Exception {
        log.debug("Registering event listener for queue: {}", EventSource.getQueueName(listener));
        if (listener.requiresQpid()) {
            this.messageReceivers.add(new QpidEventMessageReceiver(listener, this.connection, mapper));
        }
        else {
            this.messageReceivers.add(new EventMessageReceiver(listener, this.connection, mapper));
        }
    }

    private void closeEventReceivers() {
        this.messageReceivers.forEach(MessageReceiver::close);
    }

    @Override
    public void onStatusUpdate(QpidStatus oldStatus, QpidStatus newStatus) {
        if (newStatus.equals(oldStatus)) {
            return;
        }

        log.debug("EventSource was notified of a QpidStatus change: {}", newStatus);
        for (MessageReceiver receiver : this.messageReceivers) {
            if (!receiver.requiresQpid()) {
                continue;
            }

            switch (newStatus) {
                case FLOW_STOPPED:
                case MISSING_BINDING:
                case MISSING_EXCHANGE:
                case DOWN:
                    receiver.pause();
                    break;
                case CONNECTED:
                    receiver.resume();
                    break;
                default:
                    // do nothing
            }
        }
    }

    /**
     * Called when the ActiveMQStatusMonitor determines that the connection to the
     * ActiveMQ broker has changed.
     *
     * @param oldStatus the old status of the broker.
     * @param newStatus the current status of the broker.
     */
    @Override
    public void onStatusUpdate(ActiveMQStatus oldStatus, ActiveMQStatus newStatus) {
        log.debug("ActiveMQ status has been updated: {}:{}", oldStatus, newStatus);
        if (ActiveMQStatus.DOWN.equals(newStatus) && !ActiveMQStatus.DOWN.equals(oldStatus)) {
            log.info("Shutting down all message receivers because the broker went down.");
            shutDown();
        }
        else if (ActiveMQStatus.CONNECTED.equals(newStatus) && !ActiveMQStatus.CONNECTED.equals(oldStatus)) {
            log.info("Connecting to message broker and initializing all message listeners.");

            // Attempt a shutdown to be sure that all resources are cleared.
            shutDown();

            this.messageReceivers.forEach(receiver -> {
                try {
                    receiver.connect();
                }
                catch (Exception e) {
                    log.warn("Unable to reconnect message listeners. Messages will not be received: {}",
                        receiver.getQueueAddress(), e);
                }
            });
        }
    }

    public static String getQueueName(EventListener listener) {
        return MessageAddress.EVENT_ADDRESS_PREFIX + "." + listener.getClass().getCanonicalName();
    }

}
