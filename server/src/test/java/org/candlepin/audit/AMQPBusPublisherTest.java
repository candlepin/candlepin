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

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.candlepin.audit.Event.Target;
import org.candlepin.audit.Event.Type;
import org.candlepin.guice.PrincipalProvider;
import org.candlepin.model.Consumer;
import org.candlepin.test.TestUtil;
import org.candlepin.util.Util;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Map;

import javax.jms.JMSException;
import javax.jms.TextMessage;
import javax.jms.TopicPublisher;
import javax.jms.TopicSession;
/**
 * AMQPBusPublisherTest
 */
public class AMQPBusPublisherTest {

    private ObjectMapper mapper;
    private TopicSession session;
    private AMQPBusPublisher publisher;
    private Map<Target, Map<Type, TopicPublisher>> publisherMap;

    @Before
    public void init() {
        mapper = new ObjectMapper();

        session = mock(TopicSession.class);

        publisherMap = Util.newMap();

        // tried using AMQPBusPubProvider to create the publisher but it
        // tries to make a real connection to a Qpid broker which we don't
        // want. We just want to make sure the publisher is making the
        // correct calls. So we have mocked out what we could for testing.
        populateTopicMap(publisherMap);
        publisher = new AMQPBusPublisher(session, publisherMap, mapper);
    }

    @Test
    public void testClose() throws JMSException {
        publisher.close();
        for (Target target : Target.values()) {
            for (Type type : Type.values()) {
                verify(publisherMap.get(target).get(type)).close();
            }
        }
    }

    @Test
    public void testApply() throws IOException {
        PrincipalProvider pp = mock(PrincipalProvider.class);
        when(pp.get()).thenReturn(TestUtil.createPrincipal("admin", null, null));

        EventFactory factory = new EventFactory(pp);
        Consumer c = TestUtil.createConsumer();
        Event e = factory.consumerCreated(c);

        String value = publisher.apply(e);

        Event e1 = mapper.readValue(value, Event.class);
        assertEquals(e.getType(), e1.getType());
        assertEquals(e.getTarget(), e1.getTarget());
    }

    @Test
    public void onEvent() throws JMSException {
        PrincipalProvider pp = mock(PrincipalProvider.class);
        when(pp.get()).thenReturn(TestUtil.createPrincipal("admin", null, null));

        EventFactory factory = new EventFactory(pp);
        Consumer c = TestUtil.createConsumer();
        Event e = factory.consumerCreated(c);
        TopicPublisher tp = publisherMap.get(Target.CONSUMER).get(Type.CREATED);

        publisher.onEvent(e);

        verify(session).createTextMessage(anyString());
        verify(tp).send(any(TextMessage.class));
    }

    // Modified from AMQPBusPubProvider.
    public void populateTopicMap(Map<Target, Map<Type, TopicPublisher>> pm) {

        for (Target target : Target.values()) {
            Map<Type, TopicPublisher> typeToTpMap = Util.newMap();
            for (Type type : Type.values()) {

                TopicPublisher tp = mock(TopicPublisher.class);
                typeToTpMap.put(type, tp);
            }
            pm.put(target, typeToTpMap);
        }
    }

}
