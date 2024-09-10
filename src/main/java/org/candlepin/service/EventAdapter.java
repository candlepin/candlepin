/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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
package org.candlepin.service;

import org.candlepin.service.model.AdapterEvent;

/**
 * An interface for publishing Candlepin {@link AdapterEvent}s.
 */
public interface EventAdapter {

    /**
     * An application lifecycle method called during the initialization sequence.
     */
    void initialize();

    /**
     * An application lifecycle method called during the shutdown sequence.
     */
    void shutdown();

    /**
     * Publishes the provided {@link AdapterEvent} using the event's type and body. The provided event type on
     * the {@link AdapterEvent} provides a mechanism for conditionally routing the events. If the provided
     * event is null, event body is null, or the event type is null, the publish operation should not be
     * performed.
     *
     * @param event
     *  an event to be published
     */
    void publish(AdapterEvent event);
}
