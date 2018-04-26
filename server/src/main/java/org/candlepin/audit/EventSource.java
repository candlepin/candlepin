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

import org.apache.activemq.artemis.api.core.TransportConfiguration;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.core.remoting.impl.invm.InVMConnectorFactory;
import org.candlepin.controller.QpidStatusListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

/**
 * EventSource
 */
public class EventSource implements QpidStatusListener {
    private static  Logger log = LoggerFactory.getLogger(EventSource.class);

    private ClientSessionFactory factory;
    private ObjectMapper mapper;
    private List<MessageReceiver> messageReceivers = new LinkedList<>();


    @Inject
    public EventSource(ObjectMapper mapper) {
        this.mapper = mapper;

        try {
            factory =  createSessionFactory();
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * @return new instance of {@link ClientSessionFactory}
     * @throws Exception
     */
    protected ClientSessionFactory createSessionFactory() throws Exception {
        return ActiveMQClient.createServerLocatorWithoutHA(
            new TransportConfiguration(InVMConnectorFactory.class.getName())).createSessionFactory();
    }

    protected void shutDown() {
        closeEventReceivers();
        factory.close();
    }

    void registerListener(EventListener listener) throws Exception {
        if (listener.requiresQpid()) {
            this.messageReceivers.add(new QpidEventMessageReceiver(listener, factory, mapper));
        }
        else {
            this.messageReceivers.add(new EventMessageReceiver(listener, factory, mapper));
        }
    }

    private void closeEventReceivers() {
        this.messageReceivers.forEach(MessageReceiver::close);
    }

    @Override
    public void onStatusUpdate(QpidStatus oldStatus, QpidStatus newStatus) {
        if (newStatus.equals(oldStatus)) {
            log.debug("Status has not changed.");
            return;
        }

        log.debug("EventSource was notified of a QpidStatus change: {}", newStatus);
        for (MessageReceiver receiver : this.messageReceivers) {
            if (!receiver.requiresQpid()) {
                continue;
            }

            if (QpidStatus.FLOW_STOPPED.equals(newStatus) || QpidStatus.DOWN.equals(newStatus)) {
                log.debug("Stopping session for EventReciever.");
                receiver.stopSession();
            }
            else if (QpidStatus.CONNECTED.equals(newStatus)) {
                log.debug("Starting session for EventReciever.");
                receiver.startSession();
            }
        }
    }

    public static String getQueueName(EventListener listener) {
        return MessageAddress.EVENT_ADDRESS_PREFIX + "." + listener.getClass().getCanonicalName();
    }
}
