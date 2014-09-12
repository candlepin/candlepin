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
package org.candlepin.gutterball.receiver;

import org.candlepin.gutterball.bsoncallback.EventCallback;
import org.candlepin.gutterball.eventhandler.EventManager;
import org.candlepin.gutterball.model.Event;

import com.google.inject.Inject;
import com.google.inject.persist.UnitOfWork;
import com.mongodb.util.JSON;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

/**
 * A JMS message listener that is invoked when Gutterball receives an
 * Event from on the bus.
 */
public class EventMessageListener implements MessageListener {

    private static Logger log = LoggerFactory.getLogger(EventMessageListener.class);

    private UnitOfWork unitOfWork;
    private EventManager eventManager;
    private EventCallback eventCallback;

    @Inject
    public EventMessageListener(UnitOfWork unitOfWork, EventManager eventManager, EventCallback eventCallback) {
        this.unitOfWork = unitOfWork;
        this.eventManager = eventManager;
        this.eventCallback = eventCallback;
    }

    @Override
    public void onMessage(Message message) {
        log.info(message.toString());

        try {
            String messageBody = getMessageBody(message);
            Event event = (Event) JSON.parse(messageBody, eventCallback);
            unitOfWork.begin();
            eventManager.handle(event);
            log.info("Received Event: " + event);
        }
        catch (Exception e) {
            log.error("Failed to decode and store event ", e);
        }
        finally {
            unitOfWork.end();
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
