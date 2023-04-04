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


import org.candlepin.async.impl.ActiveMQSessionFactory;

import java.util.Collection;

/**
 * This factory is responsible for building all of the message receivers that will be used
 * for receiving/listening messages from the message bus.
 *
 */
public interface MessageSourceReceiverFactory {

    /**
     * Builds a collection of message receivers to be used by the message source.
     *
     * @param sessionFactory the session factory of the message broker.
     * @return a collection of message receivers that will receive messages from the message source.
     */
    Collection<MessageReceiver> get(ActiveMQSessionFactory sessionFactory);

}
