/**
 * Copyright (c) 2009 - 2012 Red Hat, Inc.
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

import com.google.inject.Inject;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.candlepin.common.config.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;

/**
 * ActiveMQ Health Check Implementation Class.
 */
public class ActiveMQHealthCheck implements ActiveMQHealth {
    private static Logger log = LoggerFactory.getLogger(ActiveMQHealthCheck.class);
    private Configuration config;
    private EventSinkConnection connection;

    @Inject
    public ActiveMQHealthCheck(Configuration config, EventSinkConnection connection) {
        this.config = config;
        this.connection = connection;
    }

    @Override
    public List<QueueStatus> getQueueInfo() {
        List<QueueStatus> results = new LinkedList<>();
        try (ClientSession session = this.connection.createClientSession()) {
            session.start();
            for (String listenerClassName : ActiveMQContextListener.getActiveMQListeners(config)) {
                String queueName = "event." + listenerClassName;
                long msgCount = session.queueQuery(new SimpleString(queueName)).getMessageCount();
                results.add(new QueueStatus(queueName, msgCount));
            }
        }
        catch (Exception e) {
            log.error("Error looking up ActiveMQ queue info: ", e);
        }
        return results;
    }
}
