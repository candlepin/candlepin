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
import com.google.inject.Inject;
import com.google.inject.Injector;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.candlepin.async.JobManager;
import org.candlepin.async.JobMessageReceiver;
import org.candlepin.async.impl.ActiveMQSessionFactory;
import org.candlepin.async.impl.ArtemisJobMessageDispatcher;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;


/**
 * This factory is responsible for building all of the message receivers that will be used
 * for receiving messages from the Artemis message bus.
 */
public class ArtemisMessageSourceReceiverFactory implements MessageSourceReceiverFactory {
    private static Logger log = LoggerFactory.getLogger(ArtemisMessageSourceReceiverFactory.class);

    public static final String JOB_MESSAGE_FILTER_TEMPLATE =
        ArtemisJobMessageDispatcher.JOB_KEY_MESSAGE_PROPERTY + " IN (%s)";

    private Injector injector;
    private JobManager manager;
    private ObjectMapper mapper;
    private Configuration config;

    @Inject
    public ArtemisMessageSourceReceiverFactory(Injector injector, JobManager manager, ObjectMapper mapper,
        Configuration config) {
        this.injector = injector;
        this.mapper = mapper;
        this.config = config;
        this.manager = manager;
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

        int maxJobs = config.getInt(ConfigProperties.MAX_JOB_THREADS);
        String filter = buildJobFilter();
        log.info("Adding {} job receivers with filter: {}", maxJobs, filter);
        for (int i = 0; i < maxJobs; i++) {
            messageReceivers.add(new JobMessageReceiver(filter, this.manager, sessionFactory, mapper));
        }

        return messageReceivers;
    }

    @SuppressWarnings("indentation")
    private String buildJobFilter() {
        String enabledJobs = String.join(",",
            config.getList(ConfigProperties.ENABLED_ASYNC_JOBS).stream()
                .map(key -> ("'" + key) + "'")
                .collect(Collectors.toList()));

        return String.format(JOB_MESSAGE_FILTER_TEMPLATE, enabledJobs);
    }

    private MessageReceiver buildEventMessageReceiver(ActiveMQSessionFactory sessionFactory,
        EventListener listener) throws ActiveMQException {
        log.debug("Registering event listener for queue: {}", ArtemisMessageSource.getQueueName(listener));
        return listener.requiresQpid() ?
                   new QpidEventMessageReceiver(listener, sessionFactory, mapper) :
                   new DefaultEventMessageReceiver(listener, sessionFactory, mapper);
    }

}
