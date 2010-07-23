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
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.codehaus.jackson.map.ObjectMapper;
import org.hornetq.api.core.HornetQBuffer;
import org.hornetq.api.core.client.ClientMessage;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.StringWriter;


/**
 * ListenerWrapperTest
 */
public class ListenerWrapperTest {

    @Test
    public void onMessage() throws Exception {
        EventListener el = mock(EventListener.class);
        ClientMessage cm = mock(ClientMessage.class);
        ListenerWrapper lw = new ListenerWrapper(el);
        HornetQBuffer hqbuf = mock(HornetQBuffer.class);
        when(hqbuf.readString()).thenReturn(eventJson());
        when(cm.getBodyBuffer()).thenReturn(hqbuf);

        lw.onMessage(cm);

        verify(el).onEvent(any(Event.class));
    }

    @Test(expected = NullPointerException.class)
    public void onMessageNull() {
        EventListener el = Mockito.mock(EventListener.class);
        ListenerWrapper lw = new ListenerWrapper(el);

        lw.onMessage(null);
    }

    private String eventJson() throws Exception {
        ObjectMapper om = new ObjectMapper();
        StringWriter sw = new StringWriter();
        Event e = new Event();
        e.setId(10L);
        e.setConsumerId(20L);
        e.setPrincipal("not so random name");
        om.writeValue(sw, e);
        return sw.toString();
    }

}
