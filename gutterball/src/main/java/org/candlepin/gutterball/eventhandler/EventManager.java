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

import org.candlepin.gutterball.model.Event;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * EventManager takes an event routes it to
 * the proper handler, which should store
 * event details specific to that type of event
 */
public class EventManager {

    private static Logger log = LoggerFactory.getLogger(EventManager.class);

    public static final String CREATED_EVENT_TYPE = "CREATED";
    public static final String MODIFIED_EVENT_TYPE = "MODIFIED";
    public static final String DELETED_EVENT_TYPE = "DELETED";

    protected Map<String, EventHandler> targetHandlers;

    @Inject
    public EventManager(Map<String, EventHandler> targetHandlers) {
        this.targetHandlers = targetHandlers;
    }

    /**
     * Properly stores events by routing them
     * to event handlers
     * @param event to store
     */
    public void handle(Event event) {
        EventHandler handler = targetHandlers.get(event.getTarget());
        if (handler != null) {
            log.info("Handling " + event + " with handler: " + handler.getClass().getSimpleName());
            String eventType = event.getType();
            if (MODIFIED_EVENT_TYPE.equals(eventType)) {
                event.setStatus(handler.handleUpdated(event));
            }
            else if (CREATED_EVENT_TYPE.equals(eventType)) {
                event.setStatus(handler.handleCreated(event));
            }
            else if (DELETED_EVENT_TYPE.equals(eventType)) {
                event.setStatus(handler.handleDeleted(event));
            }
            else {
                log.warn("Got an event of unknown type: " + eventType);
                event.setStatus(Event.Status.SKIPPED);
            }
        }
        else {
            event.setStatus(Event.Status.SKIPPED);
        }
    }
}
