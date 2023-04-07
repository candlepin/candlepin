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
package org.candlepin.messaging;



/**
 * Listener interface for processing messages asynchronously
 */
public interface CPMMessageListener {

    /**
     * Called when a message has been received by a consumer to which this listener is registered.
     *
     * @param session
     *  the session owning the consumer which received the message
     *
     * @param consumer
     *  the consumer which received the message
     *
     * @param message
     *  the message received
     */
    void handleMessage(CPMSession session, CPMConsumer consumer, CPMMessage message);

}
