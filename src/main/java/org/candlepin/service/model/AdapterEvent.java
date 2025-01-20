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
package org.candlepin.service.model;

/**
 * The AdapterEvent interface represents a Candlepin event that should be published using the
 * {@link EventAdapter} and consumed by external components.
 */
public sealed interface AdapterEvent permits CloudCheckInEvent {

    /**
     * Serializes the event and returns the serialized event body text to be used when publishing the message.
     * It is the responsibility of the implementation to ensure that a body can be produced. This method
     * should not return a null value.
     *
     * @return the event body to be used when publishing the event
     */
    String getBody();

    /**
     * Returns the {@link SerializationType} used to serialize body for this event. This method should not
     * return a null value.
     *
     * @return the serialization type used to serialize the event body
     */
    SerializationType getSerializationType();

}
