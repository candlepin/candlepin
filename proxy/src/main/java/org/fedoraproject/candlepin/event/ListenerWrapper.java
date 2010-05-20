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
import org.hornetq.api.core.client.ClientMessage;
import org.hornetq.api.core.client.MessageHandler;

/**
 * ListnerWrapper
 */
public class ListenerWrapper implements MessageHandler {

    private EventListener listener;
    
    public ListenerWrapper(EventListener listener) {
        this.listener = listener;
    }
    
    @Override
    public void onMessage(ClientMessage msg) {
        listener.onEvent(new Event(msg.getBodyBuffer().readString()));
        try {
            msg.acknowledge();
        }
        catch (HornetQException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
    }

}
