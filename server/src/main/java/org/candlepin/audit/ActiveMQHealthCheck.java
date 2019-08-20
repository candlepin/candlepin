/**
 * Copyright (c) 2009 - 2019 Red Hat, Inc.
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

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.candlepin.common.config.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * ActiveMQ Health Check Implementation Class.
 */
public class ActiveMQHealthCheck implements ActiveMQHealth, Callable<HashMap<String, QueueStatus>> {
    private static Logger log = LoggerFactory.getLogger(ActiveMQHealthCheck.class);
    private Configuration config;
    private EventSinkConnection connection;

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

    @Override
    public HashMap<String, QueueStatus> queueHealth() {
        HashMap<String, QueueStatus> queueHealthStatus = new HashMap<>();
        try (ClientSession session = this.connection.createClientSession()) {
            session.start();
            for (String listenerClassName : ActiveMQContextListener.getActiveMQListeners(config)) {
                String queueName = "event." + listenerClassName;
                long msgCount = session.queueQuery(new SimpleString(queueName)).getMessageCount();
                String msgId = getMessageId(session, queueName);
                queueHealthStatus.put(queueName, new QueueStatus(queueName, msgCount, msgId));
            }
        }
        catch (ActiveMQException e) {
            log.error("Error collecting data points for queue health: ", e);
        }
        return queueHealthStatus;
    }

    private String getMessageId(ClientSession session, String queueName) throws ActiveMQException {
        try {
            ClientConsumer consumer = session.createConsumer(queueName, true);
            ClientMessage clientMsg = consumer.receive(1000);
            return String.valueOf(clientMsg.getMessageID());
        }
        catch (ActiveMQException exp) {
            log.error("Unable to get message from queue {}", queueName);
            throw exp;
        }
    }

    @Override
    public HashMap<String, QueueStatus> call() throws Exception {
        return queueHealth();
    }
}
