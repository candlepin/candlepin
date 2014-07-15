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

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.HornetQExceptionType;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * EventSource
 */
public class EventSource {
    private static  Logger log = LoggerFactory.getLogger(HornetqContextListener.class);
    static final String QUEUE_ADDRESS = "event";
    private ClientSession session;
    private ObjectMapper mapper;

    @Inject
    public EventSource(ObjectMapper mapper) {
        this.mapper = mapper;

        try {
            ClientSessionFactory factory =  createSessionFactory();
            session = factory.createSession(true, true);
            session.start();
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
        return HornetQClient.createServerLocatorWithoutHA(
            new TransportConfiguration(
                InVMConnectorFactory.class.getName())).createSessionFactory();
    }

    void shutDown() {
        try {
            session.stop();
            session.close();
        }
        catch (HornetQException e) {
            log.warn("Exception while trying to shutdown hornetq", e);
        }
    }

    void registerListener(EventListener listener) {
        String queueName = QUEUE_ADDRESS + "." + listener.getClass().getCanonicalName();
        log.debug("registering listener for " + queueName);
        try {
            try {
                session.createQueue(QUEUE_ADDRESS, queueName);
                log.debug("created new event queue " + queueName);
            }
            catch (HornetQException e) {
                // if the queue exists already we already created it in a previous run,
                // so that's fine.
                if (e.getType() != HornetQExceptionType.QUEUE_EXISTS) {
                    throw e;
                }
            }

            ClientConsumer consumer = session.createConsumer(queueName);
            consumer.setMessageHandler(new ListenerWrapper(listener, mapper));
        }
        catch (HornetQException e) {
            log.error("Unable to register listener :" + listener, e);
        }
    }
}
