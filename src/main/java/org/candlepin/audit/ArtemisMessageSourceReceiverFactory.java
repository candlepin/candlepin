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
import org.candlepin.config.Configuration;

import com.google.inject.Injector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import tools.jackson.databind.ObjectMapper;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

import javax.inject.Inject;


/**
 * This factory is responsible for building all of the message receivers that will be used
 * for receiving messages from the Artemis message bus.
 */
public class ArtemisMessageSourceReceiverFactory implements MessageSourceReceiverFactory {
    private static Logger log = LoggerFactory.getLogger(ArtemisMessageSourceReceiverFactory.class);

    private Injector injector;
    private ObjectMapper mapper;
    private Configuration config;

    @Inject
    public ArtemisMessageSourceReceiverFactory(Injector injector, ObjectMapper mapper, Configuration config) {
        this.injector = injector;
        this.mapper = mapper;
        this.config = config;
    }

    @Override
    public Collection<MessageReceiver> get(ActiveMQSessionFactory sessionFactory) {
        List<MessageReceiver> messageReceivers = new LinkedList<>();

        // Build up the collection of Event message receivers.
        ActiveMQContextListener.getActiveMQListeners(this.config).forEach(listenerClass -> {
            try {
                Class<?> clazz = this.getClass().getClassLoader().loadClass(listenerClass);
                messageReceivers.add(buildEventMessageReceiver(sessionFactory,
                    (EventListener) injector.getInstance(clazz)));
            }
            catch (Exception e) {
                log.warn("Unable to register listener {}", listenerClass, e);
            }
        });

        return messageReceivers;
    }

    private MessageReceiver buildEventMessageReceiver(ActiveMQSessionFactory sessionFactory,
        EventListener listener) {

        log.debug("Registering event listener for queue: {}", ArtemisMessageSource.getQueueName(listener));
        return new DefaultEventMessageReceiver(listener, sessionFactory, mapper);
    }

}
