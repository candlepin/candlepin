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

package org.candlepin.gutterball.eventhandler;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.gutterball.curator.ConsumerStateCurator;
import org.candlepin.gutterball.model.ConsumerState;
import org.candlepin.gutterball.model.Event;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.Date;

@RunWith(MockitoJUnitRunner.class)
public class ConsumerHandlerTest {

    @Mock
    private ConsumerStateCurator consumerStateCurator;

    @Mock
    private ObjectMapper mapper;

    private ConsumerHandler handler;

    @Before
    public void setupTest() {
        handler = new ConsumerHandler(mapper, consumerStateCurator);
    }

    @Test
    public void testCuratorCreatesNewConsumerStateOnHandlingCreatedEvent() throws Exception {
        Event event = new Event();
        event.setNewEntity("test-string");

        ConsumerState state = new ConsumerState("test-uuid", "owner-key", new Date());
        when(mapper.readValue(eq(event.getNewEntity()), eq(ConsumerState.class))).thenReturn(state);
        when(consumerStateCurator.findByUuid(state.getUuid())).thenReturn(null);

        handler.handleCreated(event);
        verify(consumerStateCurator).create(eq(state));
    }

    @Test
    public void testConsumerStateCreationSkippedIfRecordAlreadyExists() throws Exception {
        Event event = new Event();
        event.setNewEntity("test-string");

        ConsumerState state = new ConsumerState("test-uuid", "owner-key", new Date());
        when(mapper.readValue(eq(event.getNewEntity()), eq(ConsumerState.class))).thenReturn(state);
        when(consumerStateCurator.findByUuid(eq("test-uuid"))).thenReturn(state);

        handler.handleCreated(event);
        verify(consumerStateCurator).findByUuid(eq("test-uuid"));
        verifyNoMoreInteractions(consumerStateCurator);
    }

    @Test
    public void testCuratorUpdatesDeletedConsumerStateOnDeletedEvent() throws Exception {
        Event event = new Event();
        event.setOldEntity("test-string");

        ConsumerState state = new ConsumerState("test-uuid", "owner-key", new Date());
        when(mapper.readValue(eq(event.getOldEntity()), eq(ConsumerState.class))).thenReturn(state);

        handler.handleDeleted(event);
        verify(consumerStateCurator).setConsumerDeleted(eq(state.getUuid()), any(Date.class));
    }

    @Test
    public void testConsumerStateIsNotUpdatedOnUpdateEvent() {
        handler.handleUpdated(new Event());
        verifyZeroInteractions(consumerStateCurator);
    }

}
