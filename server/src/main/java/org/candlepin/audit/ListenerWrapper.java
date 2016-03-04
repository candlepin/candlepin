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

import java.io.IOException;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * ListnerWrapper
 */
public class ListenerWrapper implements MessageListener {

    private EventListener listener;
    private static Logger log = LoggerFactory.getLogger(ListenerWrapper.class);
    private ObjectMapper mapper;
    public ListenerWrapper(EventListener listener, ObjectMapper mapper) {
        this.listener = listener;
        this.mapper = mapper;
    }

    @Override
    public void onMessage(Message msg) {
        String body = "";
        try {
            body = ((TextMessage) msg).getText();
            if (log.isDebugEnabled()) {
                log.debug("Got event: {}", body);
            }
        }
        catch (Exception ex){
            throw new RuntimeException("Couldn't read text from the JMS message");
        }

        // Exceptions thrown here will cause the event to remain in hornetq:
        try {
            Event event = mapper.readValue(body, Event.class);
            listener.onEvent(event);
        }
        catch (JsonMappingException e) {
            log.error("Unable to deserialize event object from msg: " + body, e);
            throw new RuntimeException("Error deserializing event", e);
        }
        catch (JsonParseException e) {
            log.error("Unable to deserialize event object from msg: " + body, e);
            throw new RuntimeException("Error deserializing event", e);
        }
        catch (IOException e) {
            log.error("Unable to deserialize event object from msg: " + body, e);
            throw new RuntimeException("Error deserializing event", e);
        }

        try {
            msg.acknowledge();
            log.debug("Hornetq message acknowledged for listener: " + listener);
        }
        catch (Exception e) {
            log.error("Unable to acknowledge hornetq msg", e);
        }
    }


}
