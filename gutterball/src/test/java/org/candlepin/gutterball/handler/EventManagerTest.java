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
package org.candlepin.gutterball.handler;

import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;

import org.candlepin.gutterball.curator.EventCurator;
import org.candlepin.gutterball.eventhandler.EventHandler;
import org.candlepin.gutterball.eventhandler.EventManager;
import org.candlepin.gutterball.model.Event;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.HashMap;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class EventManagerTest {

    private static final String TEST_HANDLER_TARGET = "HANDER_TARGET";

    @Mock
    private EventCurator eventCurator;

    @Mock
    private EventHandler handler;

    private EventManager eventManager;

    @Before
    public void before() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        Map<String, EventHandler> handlers = new HashMap<String, EventHandler>();
        handlers.put(TEST_HANDLER_TARGET, handler);
        eventManager = new TestingEventManager(handlers);
    }

    @Test
    public void verifyHandleCreated() {
        Event toHandle = new Event();
        toHandle.setTarget(TEST_HANDLER_TARGET);
        toHandle.setType(EventManager.CREATED_EVENT_TYPE);
        eventManager.handle(toHandle);
        verify(handler).handleCreated(eq(toHandle));
        verify(handler, never()).handleUpdated(any(Event.class));
        verify(handler, never()).handleDeleted(any(Event.class));
    }

    @Test
    public void verifyHandleUpdated() {
        Event toHandle = new Event();
        toHandle.setTarget(TEST_HANDLER_TARGET);
        toHandle.setType(EventManager.MODIFIED_EVENT_TYPE);
        eventManager.handle(toHandle);
        verify(handler).handleUpdated(eq(toHandle));
        verify(handler, never()).handleCreated(any(Event.class));
        verify(handler, never()).handleDeleted(any(Event.class));
    }

    @Test
    public void verifyHandleDeleted() {
        Event toHandle = new Event();
        toHandle.setTarget(TEST_HANDLER_TARGET);
        toHandle.setType(EventManager.DELETED_EVENT_TYPE);
        eventManager.handle(toHandle);
        verify(handler).handleDeleted(eq(toHandle));
        verify(handler, never()).handleCreated(any(Event.class));
        verify(handler, never()).handleUpdated(any(Event.class));
    }

    @Test
    public void testEventManagerUnknown() {
        Event toHandle = new Event();
        toHandle.setTarget("UNKNOWN_EVENT_TARGET");
        eventManager.handle(toHandle);
        verify(handler, never()).handleCreated(any(Event.class));
        verify(handler, never()).handleUpdated(any(Event.class));
        verify(handler, never()).handleDeleted(any(Event.class));
    }

    @Test
    public void testEventManagerNullTarget() {
        Event toHandle = new Event();
        eventManager.handle(toHandle);
        verify(handler, never()).handleCreated(any(Event.class));
        verify(handler, never()).handleUpdated(any(Event.class));
        verify(handler, never()).handleDeleted(any(Event.class));
    }

    // Class allows us to override loadEventHandlers, so we can supply mocks
    // We aren't testing the DB, so we want to avoid the curators
    private class TestingEventManager extends EventManager {

        public TestingEventManager(Map<String, EventHandler> handlers) {
            super(handlers);
        }
    }
}
