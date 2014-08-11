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

/**
 * EventHandler interface which provides a structure for
 * handling various types of events
 */
public interface EventHandler {

    /**
     * Handles creation events
     * @param event Event to store
     */
    void handleCreated(Event event);

    /**
     * Handles modification events
     * @param event Event to store
     */
    void handleUpdated(Event event);

    /**
     * Handles deletion events
     * @param event Event to store
     */
    void handleDeleted(Event event);
}
