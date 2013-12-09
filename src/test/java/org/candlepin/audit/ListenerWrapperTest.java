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
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.auth.PrincipalData;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.hornetq.api.core.HornetQBuffer;
import org.hornetq.api.core.HornetQBuffers;
import org.hornetq.api.core.HornetQException;
import org.hornetq.api.core.HornetQExceptionType;
import org.hornetq.api.core.client.ClientMessage;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.runners.MockitoJUnitRunner;

import java.io.StringWriter;


/**
 * ListenerWrapperTest
 */
@RunWith(MockitoJUnitRunner.class)
public class ListenerWrapperTest {
    @Mock private EventListener mockEventListener;
    @Mock private ClientMessage mockClientMessage;
    @Spy private ObjectMapper mapper = new ObjectMapper();
    @Spy private HornetQBuffer hornetQBuffer = HornetQBuffers.fixedBuffer(1000);
    private ListenerWrapper listenerWrapper;

    @Before
    public void init() {
        this.listenerWrapper = new ListenerWrapper(mockEventListener, mapper);
        when(mockClientMessage.getBodyBuffer())
            .thenReturn(hornetQBuffer);
    }

    @Test
    public void whenMapperReadThrowsExceptionThenOnMessageShouldntFail() throws Exception {
        doReturn("test123").when(hornetQBuffer).readString();
        doThrow(new JsonMappingException("Induced exception"))
            .when(mapper).readValue(anyString(), eq(Event.class));
        this.listenerWrapper.onMessage(mockClientMessage);
        verify(this.mockClientMessage).acknowledge();
    }

    @Test
    public void whenMsgAcknowledgeThrowsExceptionThenOnMessageShouldntFail()
        throws Exception {
        doReturn(eventJson()).when(hornetQBuffer).readString();
        doThrow(new HornetQException(HornetQExceptionType.DISCONNECTED,
            "Induced exception for junit testing"))
            .when(mockClientMessage).acknowledge();
        this.listenerWrapper.onMessage(mockClientMessage);
        verify(this.mockEventListener).onEvent(any(Event.class));
    }

    @Test
    public void whenProperClientMsgPassedThenOnMessageShouldSucceed()
        throws Exception {
        doReturn(eventJson()).when(hornetQBuffer).readString();
        this.listenerWrapper.onMessage(mockClientMessage);
        verify(this.mockEventListener).onEvent(any(Event.class));
        verify(this.mockClientMessage).acknowledge();
    }

    @Test(expected = NullPointerException.class)
    public void onMessageNull() {
        this.listenerWrapper.onMessage(null);
    }

    private String eventJson() throws Exception {
        StringWriter sw = new StringWriter();
        Event e = new Event();
        e.setId("10");
        e.setConsumerId("20");
        e.setPrincipal(new PrincipalData("5678", "910112"));
        mapper.writeValue(sw, e);
        return sw.toString();
    }

}
