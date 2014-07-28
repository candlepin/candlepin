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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.candlepin.gutterball.curator.EventCurator;
import org.candlepin.gutterball.model.Event;
import org.candlepin.gutterball.util.EventHandlerLoader;

import com.google.inject.Inject;
import com.google.inject.Injector;

/**
 * EventManager takes an event routes it to
 * the proper handler, which should store
 * event details specific to that type of event
 */
public class EventManager {

    private static final String CREATED = "CREATED";
    private static final String MODIFIED = "MODIFIED";
    private static final String DELETED = "DELETED";

    protected Map<String, EventHandler> targetHandler;
    private EventCurator eventCurator;
    private Injector injector;

    @Inject
    public EventManager(Injector injector, EventCurator eventCurator) {
        this.injector = injector;
        this.eventCurator = eventCurator;
        targetHandler = new HashMap<String, EventHandler>();
        loadEventHandlers();
    }

    protected void loadEventHandlers() {
        List<Class<? extends EventHandler>> handlers = EventHandlerLoader.getClasses();
        for (Class<? extends EventHandler> handlerClass : handlers) {
            EventHandler handler = injector.getInstance(handlerClass);
            targetHandler.put(handler.getTarget(), handler);
        }
    }

    /**
     * Properly stores events by routing them
     * to event handlers
     * @param event to store
     */
    public void handle(Event event) {
        // Store every event
        eventCurator.insert(event);

        EventHandler handler = targetHandler.get(event.getTarget());
        if (handler != null) {
            String eventType = event.getType();
            if (MODIFIED.equals(eventType)) {
                handler.handleUpdated(event);
            }
            else if (CREATED.equals(eventType)) {
                handler.handleCreated(event);
            }
            else if (DELETED.equals(eventType)) {
                handler.handleDeleted(event);
            }
        }
    }
}
