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

import org.candlepin.controller.ActiveMQStatusListener;

/**
 * Represents the source of messages that are to be received from candlepin's message bus.
 * A message source should set up any message listeners that are required to receive messages
 * from the underlying message broker. Message listeners should only be connected/active when
 * the message bus connection has been established.
 */
public interface MessageSource extends ActiveMQStatusListener {

    /**
     * Shuts down this message source. All resources should be cleaned up here.
     */
    void shutDown();

}
