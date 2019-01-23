/**
 * Copyright (c) 2009 - 2016 Red Hat, Inc.
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.candlepin.async.impl.ActiveMQSessionFactory;

/**
 * An abstract base class for all message receivers listening for Event messages.
 */
public abstract class EventMessageReceiver extends MessageReceiver {

    protected EventListener listener;

    public EventMessageReceiver(EventListener listener, ActiveMQSessionFactory sessionFactory,
        ObjectMapper mapper) {
        super(ArtemisMessageSource.getQueueName(listener), sessionFactory, mapper);
        this.listener = listener;
    }

    // FIXME This should not be determined by the listener once the class is created
    //       since the QpidEventMessageReceiver already knows that Qpid is required.
    @Override
    public boolean requiresQpid() {
        return this.listener.requiresQpid();
    }

    @Override
    protected void initialize() throws Exception {
        session = this.sessionFactory.getIngressSession(false);
        consumer = session.createConsumer(queueName);
        consumer.setMessageHandler(this);
        session.start();
    }

}
