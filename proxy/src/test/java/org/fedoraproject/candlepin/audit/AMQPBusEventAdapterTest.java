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

import static org.hamcrest.collection.IsMapContaining.hasEntry;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.anyMap;
import static org.mockito.Matchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.fedoraproject.candlepin.auth.Principal;
import org.fedoraproject.candlepin.pki.PKIReader;
import org.fedoraproject.candlepin.pki.PKIUtility;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Testing common functionality for all event types and supported event targets.
 * "Supported" in this case refers to event targets that are emitted as AMQP
 * messages.
 */
@RunWith(Parameterized.class)
public class AMQPBusEventAdapterTest {

    @Mock
    private ObjectMapper mapper;
    @Mock
    private Principal principal;

    private AMQPBusEventAdapter eventAdapter;
    private Event event;

    private Event.Type type;
    private Event.Target target;
    @Mock
    private PKIReader reader;
    @Mock
    private PKIUtility pkiutil;

    /**
     * This defines the event targets that we care about creating AMQP events
     * for.
     */
    private static final Event.Target[] SUPPORTED_TARGETS = new Event.Target[]{
        Event.Target.CONSUMER, Event.Target.SUBSCRIPTION };

    /**
     * Defines the test data.
     *
     * @return the parameterized data for the JUnit runner
     */
    @Parameters
    public static List<Object[]> data() {
        List<Object[]> data = new ArrayList<Object[]>();

        for (Event.Type type : Event.Type.values()) {
            for (Event.Target target : SUPPORTED_TARGETS) {
                data.add(new Object[]{ type, target });
            }
        }

        return data;
    }

    public AMQPBusEventAdapterTest(Event.Type type, Event.Target target) {
        this.type = type;
        this.target = target;
    }

    @Before
    public void init() throws IOException {
        MockitoAnnotations.initMocks(this);

        // given
        this.eventAdapter = new AMQPBusEventAdapter(mapper, reader, pkiutil);
        this.event = new Event(this.type, this.target, "name", principal, "1",
            "1", "42", "Old Entity", "New Entity", null, null);
    }

    /**
     * Verifies that the {@link ObjectMapper} is used for map serialization.
     *
     * @throws IOException
     */
    @Test
    public void objectMapperUsed() throws IOException {
        // given
        String serializedMap = "SERIALIZED_MAP_VALUE";
        when(mapper.writeValueAsString(anyMap())).thenReturn(serializedMap);

        // when
        String translatedEvent = this.eventAdapter.apply(this.event);

        // then
        assertThat(serializedMap, is(equalTo(translatedEvent)));
    }

    /**
     * Verifies that the {@link Event}'s id is serialized in all translations.
     *
     * @throws IOException
     */
    @Test
    public void idSerialized() throws IOException {
        // when
        this.eventAdapter.apply(this.event);

        // then
        verify(mapper).writeValueAsString(
            argThat(hasEntry(equalTo("event"), hasEntry("id", "42"))));
    }

}
