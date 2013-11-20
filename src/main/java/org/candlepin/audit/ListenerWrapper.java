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

import org.codehaus.jackson.map.ObjectMapper;
import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ListnerWrapper
 */
public class ListenerWrapper implements MessageHandler {

    private EventListener listener;
    private static Logger log = LoggerFactory.getLogger(ListenerWrapper.class);
    private ObjectMapper mapper;
    public ListenerWrapper(EventListener listener, ObjectMapper mapper) {
        this.listener = listener;
        this.mapper = mapper;
    }

    @Override
    public void onMessage(ClientMessage msg) {
        String body = msg.getBodyBuffer().readString();
        if (log.isDebugEnabled()) {
            log.debug("Got event: " + body);
        }
        Event event;
        try {
            event = mapper.readValue(body, Event.class);
            listener.onEvent(event);
        }
        catch (Exception e1) {
            log.error("Unable to deserialize event object from msg: " + body, e1);
        }

        try {
            msg.acknowledge();
        }
        catch (HornetQException e) {
            log.error("Unable to ack msg", e);
        }
    }

}
