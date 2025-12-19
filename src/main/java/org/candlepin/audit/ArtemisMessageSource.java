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

import org.candlepin.async.impl.ActiveMQSessionFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

import java.util.Collection;

import javax.inject.Inject;
import javax.inject.Singleton;


/**
 * An implementation of a MessageSource backed by Artemis. This message source manages
 * a collection of MessageReceivers that listen for messages that are put in the Artemis
 * message queues.
 */
@Singleton
public class ArtemisMessageSource implements MessageSource {
    private static Logger log = LoggerFactory.getLogger(ArtemisMessageSource.class);

    private ObjectMapper mapper;
    private ActiveMQSessionFactory sessionFactory;
    private Collection<MessageReceiver> messageReceivers;

    @Inject
    public ArtemisMessageSource(ActiveMQSessionFactory sessionFactory, ObjectMapper mapper,
        MessageSourceReceiverFactory receiverFactory) {
        this.sessionFactory = sessionFactory;
        this.mapper = mapper;
        this.messageReceivers = receiverFactory.get(this.sessionFactory);
    }

    @Override
    public void shutDown() {
        closeEventReceivers();
        // TODO Need to determine if it is OK to not close the sessionFactory.
        //      On candlepin shutdown, will this be required.
//        this.sessionFactory.close();
    }

    private void closeEventReceivers() {
        this.messageReceivers.forEach(MessageReceiver::close);
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
        log.info("ActiveMQ status has been updated: {}:{}", oldStatus, newStatus);
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
