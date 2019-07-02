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
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


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

    private String amqpFilter;

    @Inject
    public ArtemisMessageSourceReceiverFactory(Injector injector, JobManager manager, ObjectMapper mapper,
        Configuration config) {

        this.injector = injector;
        this.mapper = mapper;
        this.config = config;
        this.manager = manager;

        this.amqpFilter = this.buildAMQPFilterExpression();
    }

    /**
     * Builds a filter expression to apply to the client consumers used by the message receivers
     * this factory generates. The expression returned will allow AMQP consumers to filter messages
     * based on the jobs enabled or disabled in the configuration used to build this receiver
     * factory.
     *
     * @return
     *  an AMQP filter expression for filtering jobs
     */
    private String buildAMQPFilterExpression() {
        // Default to no filtering
        String filter = null;

        Set<String> blacklist = new HashSet<>();

        // Add blacklisted jobs
        List<String> list = this.config.getList(ConfigProperties.ASYNC_JOBS_BLACKLIST, null);
        if (list != null) {
            blacklist.addAll(list);
        }

        // Add jobs explicitly disabled
        String prefix = Pattern.quote(ConfigProperties.ASYNC_JOBS_PREFIX);
        String suffix = Pattern.quote(ConfigProperties.ASYNC_JOBS_JOB_ENABLED);
        Pattern regex = Pattern.compile("\\A" + prefix + "(.+)\\." + suffix + "\\z");

        for (String key : this.config.getKeys()) {
            Matcher matcher = regex.matcher(key);

            if (matcher.matches()) {
                boolean enabled = this.config.getBoolean(key, true);

                if (!enabled) {
                    blacklist.add(matcher.group(1));
                }
            }
        }

        list = this.config.getList(ConfigProperties.ASYNC_JOBS_WHITELIST, null);
        if (list != null) {
            // Whitelist mode (inclusion!)
            list.removeAll(blacklist);

            if (list.size() > 0) {
                filter = String.format("%s IN ('%s')", ArtemisJobMessageDispatcher.JOB_KEY_MESSAGE_PROPERTY,
                    String.join("', '", list));
            }
            else {
                // FIXME: This is a hack, we should simply not have a listener/session if the
                // node will not be processing jobs.
                filter = String.format("%s = ''", ArtemisJobMessageDispatcher.JOB_KEY_MESSAGE_PROPERTY);
            }
        }
        else if (blacklist.size() > 0) {
            // Blacklist mode (exclusion)
            filter = String.format("%s NOT IN ('%s')", ArtemisJobMessageDispatcher.JOB_KEY_MESSAGE_PROPERTY,
                String.join("', '", blacklist));
        }

        log.debug("Built AMQP filter expression: {}", filter);
        return filter;
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

        int maxJobs = config.getInt(ConfigProperties.ASYNC_JOBS_THREADS);
        log.info("Adding {} job receivers with filter: {}", maxJobs, this.amqpFilter);

        for (int i = 0; i < maxJobs; i++) {
            messageReceivers.add(
                new JobMessageReceiver(this.amqpFilter, this.manager, sessionFactory, mapper));
        }

        return messageReceivers;
    }

    private MessageReceiver buildEventMessageReceiver(ActiveMQSessionFactory sessionFactory,
        EventListener listener) throws ActiveMQException {

        log.debug("Registering event listener for queue: {}", ArtemisMessageSource.getQueueName(listener));
        return listener.requiresQpid() ?
            new QpidEventMessageReceiver(listener, sessionFactory, mapper) :
            new DefaultEventMessageReceiver(listener, sessionFactory, mapper);
    }

}
