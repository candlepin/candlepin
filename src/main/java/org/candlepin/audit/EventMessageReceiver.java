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

import org.candlepin.messaging.CPMConsumerConfig;
import org.candlepin.messaging.CPMMessageListener;
import org.candlepin.messaging.CPMSessionManager;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * An abstract base class for all message receivers listening for Event messages.
 */
public abstract class EventMessageReceiver extends MessageReceiver {

    protected CPMMessageListener listener;

    public EventMessageReceiver(CPMMessageListener listener, CPMSessionManager sessionManager,
        ObjectMapper mapper) {

        super(ArtemisMessageSource.getQueueName(listener), sessionManager, mapper);
        this.listener = listener;
    }

    @Override
    protected void initialize() throws Exception {
        session = this.sessionManager.createSession(false);

        CPMConsumerConfig config = new CPMConsumerConfig()
            .setQueue(queueName);

        consumer = session.createConsumer(config);
        consumer.setMessageListener(listener);
        session.start();
    }

}
