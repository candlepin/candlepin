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
package org.candlepin.audit.qpidtest;

import java.util.Random;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * A JMS message listener that is invoked when Gutterball receives an
 * Event from on the bus.
 */
public class EventMessageListener implements MessageListener {

    private static Logger log = LoggerFactory.getLogger(EventMessageListener.class);
    private Random rand = new Random(); 
    private ObjectMapper mapper;
    private String name;

    public EventMessageListener(String name){
        this.name = name;
    }
    @Override
    public void onMessage(Message message) {
        try {
            Thread.sleep(rand.nextInt(2000));
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        try {
            System.out.println(name + ": receiving message! "+((TextMessage)message).getText().substring(0, 10));
        } catch (JMSException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }
    
    private String getMessageBody(Message message) {
        try {
            return ((TextMessage) message).getText();
        }
        catch (JMSException e) {
            log.error("failed to get text out of message", e);
            throw new RuntimeException(e);
        }
    }
}
