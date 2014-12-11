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
import org.candlepin.gutterball.model.Event.Status;


/**
 * EventHandler base class which provides a structure for
 * handling various types of events.
 */
public abstract class EventHandler {

    /**
     * Handles creation events
     * @param event Event to store
     * @return Status of the event to be set in database.
     */
    public Status handleCreated(Event event) {
        return Status.SKIPPED;
    }

    /**
     * Handles modification events
     * @param event Event to store
     * @return Status of the event to be set in database.
     */
    public Status handleUpdated(Event event) {
        return Status.SKIPPED;
    }

    /**
     * Handles deletion events
     * @param event Event to store
     * @return Status of the event to be set in database.
     */
    public Status handleDeleted(Event event) {
        return Status.SKIPPED;
    }
}
