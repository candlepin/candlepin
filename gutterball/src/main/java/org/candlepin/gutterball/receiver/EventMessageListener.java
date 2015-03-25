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

import org.candlepin.gutterball.curator.EventCurator;
import org.candlepin.gutterball.eventhandler.EventManager;
import org.candlepin.gutterball.model.Event;
import org.candlepin.gutterball.model.Event.Status;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.persist.UnitOfWork;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

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
    private ObjectMapper mapper;
    private EventCurator eventCurator;

    @Inject
    public EventMessageListener(UnitOfWork unitOfWork, ObjectMapper mapper,
            EventManager eventManager, EventCurator eventCurator) {
        this.unitOfWork = unitOfWork;
        this.eventManager = eventManager;
        this.mapper = mapper;
        this.eventCurator = eventCurator;
    }

    @Override
    public void onMessage(Message message) {
        Event event = storeEvent(message);
        // Event already exists, no need to process.
        if (event == null) {
            return;
        }
        processEvent(event);
    }

    /**
     * Initial event storage. (first phase)
     *
     * In this phase we simply want to get the event into our database.
     * Any exception thrown here indicates a very serious problem, and will end up
     * leaving the message on the bus, which will re-try delivery the next time the
     * application rejoins.
     *
     * Exceptions should always bubble up here and never be caught and ignored, as we need
     * to do everything possible to make sure events never get dropped.
     *
     * Once we've parsed the JSON we save to the database and commit the transaction.
     * Event processing will be handled separately.
     *
     * @param message Incoming JMS message from the bus.
     * @return Event parsed from the message JSON.
     */
    private Event storeEvent(Message message) {
        // TODO: get this down to debug when we have support for viewing debug logging:
        log.info(message.toString());

        String messageBody = getMessageBody(message);
        Event event = null;
        try {
            event = mapper.readValue(messageBody, Event.class);

            /*
             * Set initial event state. If event remains in this state, it indicates there
             * was an error processing it.
             */
            event.setStatus(Status.RECEIVED);

            unitOfWork.begin();

            String messageId = message.getJMSMessageID();
            if (eventCurator.hasEventForMessage(messageId)) {
                log.info("Event already created for message. Skipping message: " + messageId);
                return null;
            }

            // Store every event
            event.setMessageId(messageId);
            eventCurator.create(event);
        }
        catch (JsonParseException e) {
            log.error("Error processing event", e);
            log.error("Event message body: {}", messageBody);
            throw new RuntimeException("Error processing event", e);
        }
        catch (JsonMappingException e) {
            log.error("Error processing event", e);
            log.error("Event message body: {}", messageBody);
            throw new RuntimeException("Error processing event", e);
        }
        catch (IOException e) {
            log.error("Error processing event", e);
            log.error("Event message body: {}", messageBody);
            throw new RuntimeException("Error processing event", e);
        }
        catch (JMSException e) {
            throw new RuntimeException("Unable to get the message id when creating the event.", e);
        }
        finally {
            unitOfWork.end();
        }
        return event;
    }

    /**
     * Process the event received. (second phase)
     *
     * In this phase we do any more complex processing of the event in a separate
     * transaction from the one where we first stored the event.
     *
     * Exceptions here should always be caught and never bubble up. The transaction
     * will never be committed and the event will be left in the database with an initial
     * state that indicates there was some kind of failure in processing. This allows
     * us to identify problem events and eventually re-try processing them.
     *
     * @param event Event to be processed.
     */
    private void processEvent(Event event) {
        try {
            unitOfWork.begin();
            eventManager.handle(event);
            // Handlers alter the event status, save it:
            eventCurator.merge(event);
            unitOfWork.end();
        }
        catch (Exception e) {
            log.error("Error processing event: " + event, e);
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
            log.error("failed to get text out of message", e);
            throw new RuntimeException(e);
        }
    }
}
