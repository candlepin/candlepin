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

import org.candlepin.messaging.CPMSessionManager;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    @Inject
    public ArtemisMessageSource(CPMSessionManager manager, MessageSourceReceiverFactory receiverFactory) {
        receiverFactory.get(manager);
    }

    @Override
    public void shutDown() {
        log.info("Shutdown");
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
        log.info("onStatusUpdate");
    }

    public static String getQueueName(EventListener listener) {
        return MessageAddress.EVENT_ADDRESS_PREFIX + "." + listener.getClass().getCanonicalName();
    }

}
