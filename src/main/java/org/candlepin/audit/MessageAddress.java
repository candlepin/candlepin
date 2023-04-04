/*
 * Copyright (c) 2009 - 2023 Red Hat, Inc.
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

/**
 * Defines the addresses that are used when sending and receiving messages from Artemis.
 */
public final class MessageAddress {

    /**
     * The address prefix for all of candlepin's event message addresses.
     */
    static final String EVENT_ADDRESS_PREFIX = "event";

    /**
     * The default address that all event based messages are sent.
     */
    public static final String DEFAULT_EVENT_MESSAGE_ADDRESS =
        String.format("%s.default", EVENT_ADDRESS_PREFIX);

    /**
     * The address that job event messages are sent to.
     */
    public static final String JOB_MESSAGE_ADDRESS = "job";

    private MessageAddress() {
    }
}
