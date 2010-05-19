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
import org.hornetq.api.core.client.ClientSession;
import org.hornetq.api.core.client.ClientSessionFactory;
import org.hornetq.api.core.client.HornetQClient;
import org.hornetq.api.core.client.MessageHandler;
import org.hornetq.core.remoting.impl.invm.InVMConnectorFactory;

/**
 * OtherExampleListener
 */
public class OtherExampleListener implements MessageHandler {
    
    public OtherExampleListener() {
        try {
            ClientSessionFactory factory =  HornetQClient.createClientSessionFactory(
                new TransportConfiguration(
                   InVMConnectorFactory.class.getName()));
    
            ClientSession session = factory.createSession(true, true);
            
            session.start();
            
            ClientConsumer consumer = session.createConsumer("example");
            consumer.setMessageHandler(this);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onMessage(ClientMessage msg) {
        System.out.println(this.toString() + "I GOT A MESSAGE TOO");
        System.out.println("  message = " + msg.getBodyBuffer().readString());
        try {
            msg.acknowledge();
        }
        catch (HornetQException e) {
            e.printStackTrace();
        }
    }

}