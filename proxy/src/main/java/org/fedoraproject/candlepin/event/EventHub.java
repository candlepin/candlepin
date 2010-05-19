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
package org.fedoraproject.candlepin.event;

import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.TransportConfiguration;
import org.hornetq.api.core.client.ClientConsumer;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.ClientProducer;
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;

/**
 * EventKit
 */
public class EventHub {
    private ClientSession session;
    
    public EventHub() {
        ClientSessionFactory factory =  HornetQClient.createClientSessionFactory(
            new TransportConfiguration(
               InVMConnectorFactory.class.getName()));

        try {
            session = factory.createSession(true, true);
            session.start();
        }
        catch (HornetQException e) {
            e.printStackTrace();
        }
    }
    
    public void shutDown() {
        try {
            session.stop();
            session.close();
        }
        catch (HornetQException e) {
            e.printStackTrace();
        }
    }
    
    public void registerListener(EventListener listener) {
        try {
            try {
                session.createQueue("event", "event." + listener.getClass().getCanonicalName(), true);
            } catch (HornetQException e) {
                // XXX: does it exist already? just pass
                e.printStackTrace();
            }
            ClientConsumer consumer = session.createConsumer("event." + listener.getClass().getCanonicalName());
            consumer.setMessageHandler(new ListenerWrapper(listener));
        }
        catch (HornetQException e) {
            e.printStackTrace();
        }
    }
    
    public static void sendEvent(Event event) {
        System.out.println("sending event");
        try {
            ClientSessionFactory factory =  HornetQClient.createClientSessionFactory(
                new TransportConfiguration(
                   InVMConnectorFactory.class.getName()));
            
            ClientSession session = factory.createSession();
            
            ClientProducer producer = session.createProducer("event");
            
            ClientMessage message = session.createMessage(true);
            
            message.getBodyBuffer().writeString(event.getMessage());
            
            producer.send(message);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }
    
}
