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
package org.candlepin.gutterball.receive;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.candlepin.gutterball.eventhandler.EventManager;
import org.candlepin.gutterball.model.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;

/**
 * A JMS message listener that is invoked when Gutterball receives an
 * Event from on the bus.
 */
public class EventMessageListener implements MessageListener {

    private static Logger log = LoggerFactory.getLogger(EventMessageListener.class);

    private EventManager eventManager;

    @Inject
    public EventMessageListener(EventManager eventManager) {
        this.eventManager = eventManager;
    }

    @Override
    public void onMessage(Message message) {
        log.info(message.toString());

        try {
            String messageBody = getMessageBody(message);
            Event event = new Event(messageBody);
            eventManager.handle(event);
            log.info("Received Event: " + event);
        }
        catch (Exception e) {
            log.error("Failed to decode and store event ", e);
        }
    }

    private String getMessageBody(Message message) {
        try {
            return ((TextMessage) message).getText();
        }
        catch (JMSException e) {
            log.error("failed to get text out of message");
            // TODO: use a candlepin exception
            throw new RuntimeException(e);
        }
    }
}
