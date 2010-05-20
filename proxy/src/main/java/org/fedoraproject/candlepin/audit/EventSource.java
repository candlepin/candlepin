/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.fedoraproject.candlepin.audit;

import org.apache.log4j.Logger;
import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;

/**
 * EventSource
 */
class EventSource {
    private static  Logger log = Logger.getLogger(HornetqContextListener.class);
    static final String QUEUE_ADDRESS = "event";

    private ClientSession session;
    
    EventSource() {
        ClientSessionFactory factory =  HornetQClient.createClientSessionFactory(
            new TransportConfiguration(InVMConnectorFactory.class.getName()));

        try {
            session = factory.createSession(true, true);
            session.start();
        }
        catch (HornetQException e) {
            e.printStackTrace();
        }
    }
    
    void shutDown() {
        try {
            session.stop();
            session.close();
        }
        catch (HornetQException e) {
            e.printStackTrace();
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
                if (e.getCode() != HornetQException.QUEUE_EXISTS) {
                    throw e;
                }
            }
            
            ClientConsumer consumer = session.createConsumer(queueName);
            consumer.setMessageHandler(new ListenerWrapper(listener));
        }
        catch (HornetQException e) {
            e.printStackTrace();
        }
    }
}
