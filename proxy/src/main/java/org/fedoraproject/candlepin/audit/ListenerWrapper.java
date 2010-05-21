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

import java.io.IOException;

import org.apache.log4j.Logger;
import org.codehaus.jackson.JsonParseException;
import org.codehaus.jackson.map.JsonMappingException;
import org.codehaus.jackson.map.ObjectMapper;
import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;

/**
 * ListnerWrapper
 */
public class ListenerWrapper implements MessageHandler {

    private EventListener listener;
    private static Logger log = Logger.getLogger(ListenerWrapper.class);
    
    public ListenerWrapper(EventListener listener) {
        this.listener = listener;
    }
    
    @Override
    public void onMessage(ClientMessage msg) {
        String body = msg.getBodyBuffer().readString();
        log.debug("Got event: " + body);
        ObjectMapper mapper = new ObjectMapper();
        Event event;
        try {
            event = mapper.readValue(body, Event.class);
            listener.onEvent(event);
        }
        catch (JsonParseException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        catch (JsonMappingException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        catch (IOException e1) {
            // TODO Auto-generated catch block
            e1.printStackTrace();
        }
        try {
            msg.acknowledge();
        }
        catch (HornetQException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
