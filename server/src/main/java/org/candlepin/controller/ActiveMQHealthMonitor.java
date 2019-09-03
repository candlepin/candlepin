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

package org.candlepin.controller;

import com.google.inject.Inject;
import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.SimpleString;
import org.apache.activemq.artemis.api.core.client.ClientConsumer;
import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSession;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.candlepin.audit.ActiveMQContextListener;
import org.candlepin.audit.ActiveMQStatus;

import org.candlepin.audit.QueueStatus;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 *  A process that checks the current state of ActiveMQ Queue health & notifies any listeners
 *  when the ActiveMQ broker state has changed.
 */
public class ActiveMQHealthMonitor implements Runnable {
    private static Logger log = LoggerFactory.getLogger(ActiveMQHealthMonitor.class);

    private List<ActiveMQStatusListener> registeredListeners;
    private long monitorInterval;
    private ActiveMQStatus lastStatus;
    private ScheduledExecutorService executorService;
    private Configuration config;
    private HashMap<String, QueueStatus> lastHealthStatusMap;


    @Inject
    public ActiveMQHealthMonitor(Configuration config, ScheduledExecutorService execService) {
        this.config = config;
        this.monitorInterval = 20;
        this.registeredListeners = new LinkedList<ActiveMQStatusListener>();
        this.lastStatus = ActiveMQStatus.CONNECTED;
        executorService = Executors.newSingleThreadScheduledExecutor();
    }

    @Override
    public void run() {
        try {
            log.info("Executing ActiveMQ Queue Health Check Every {} seconds...", monitorInterval);
            ActiveMQStatus healthStatus = isQueueHealthy();
            log.info("ActiveMQ queue health status - old: {} and new: {}", lastStatus, healthStatus);
            if (healthStatus.equals(ActiveMQStatus.UNHEALTHY)) {
                notifyListeners(lastStatus, ActiveMQStatus.UNHEALTHY);
                this.lastStatus = healthStatus;
            }
        }
        finally {
            log.info("Next ActiveMQ Queue Health Check {} seconds.", monitorInterval);
        }
    }

    /**
     * Initializes activeMQ queue health check.
     */
    public void initialize() {
        executorService.scheduleWithFixedDelay(this, 10, monitorInterval, TimeUnit.SECONDS);
    }

    /**
     * Adds a listener that will be notified when the there is status changes.
     *
     * @param listener the listener to be added.
     */
    public void registerListener(ActiveMQStatusListener listener) {
        this.registeredListeners.add(listener);
    }

    /**
     *
     */
    private void notifyListeners(ActiveMQStatus oldStatus, ActiveMQStatus newStatus) {
        for (ActiveMQStatusListener listener : this.registeredListeners) {
            try {
                // If a listener throws an exception, log it, and move to the next listener.
                // We don't want to skip other listeners just because another failed.
                listener.onStatusUpdate(oldStatus, newStatus);
            }
            catch (Exception e) {
                log.warn("Failed to notify status listener.", e);
            }
        }
    }

    ActiveMQStatus isQueueHealthy() {
        ActiveMQStatus currentMQStatus = null;
        HashMap<String, QueueStatus> qpi = getQueueInfo();
        List<String> listOfQueue = ActiveMQContextListener.getActiveMQListeners(config);
        currentMQStatus = compare(qpi, lastHealthStatusMap, listOfQueue);
        this.lastHealthStatusMap = qpi;
        return ActiveMQStatus.CONNECTED;
    }

    private ActiveMQStatus compare(HashMap<String, QueueStatus> qpi,
        HashMap<String, QueueStatus> lastHealthStatusMap, List<String> listOfQueue) {
        boolean isHealthy = false;
        if (lastHealthStatusMap == null) {
            log.info("This is first run for collecting health status");
            return ActiveMQStatus.CONNECTED;
        }
        log.info("Comparing last & current health Map");
        for (String queue : listOfQueue) {
            QueueStatus lastStatus = lastHealthStatusMap.get(queue);
            QueueStatus currentStatus = qpi.get(queue);
            isHealthy = compareStatus(lastStatus, currentStatus);
            if (!isHealthy) {
                break;
            }
        }

        if (isHealthy) {
            return ActiveMQStatus.CONNECTED;
        }
        else {
            return ActiveMQStatus.UNHEALTHY;
        }
    }

    private boolean compareStatus(QueueStatus lastStatus, QueueStatus currentStatus) {
        return true;
    }


    public HashMap<String, QueueStatus> getQueueInfo() {
        HashMap<String, QueueStatus> queueInfo = new HashMap<>();
        ServerLocator locator = null;
        ClientSessionFactory factory = null;
        try {
            String serverUrl = config.getProperty(ConfigProperties.ACTIVEMQ_BROKER_URL);
            locator = ActiveMQClient.createServerLocator(serverUrl);
            ClientSessionFactory sessionfactory = locator.createSessionFactory();
            ClientSession session = sessionfactory.createSession();
            session.start();
            for (String listenerClassName : ActiveMQContextListener.getActiveMQListeners(config)) {
                String queueName = "event." + listenerClassName;
                long msgCount = session.queueQuery(new SimpleString(queueName)).getMessageCount();
                String msgId = getMessageId(session, queueName);
                log.info("Queue Name {}, pending msg {}, and ID {}", queueName, msgCount, msgId);
                queueInfo.put(queueName, new QueueStatus(queueName, msgCount, msgId));
            }
            session.close();
            return queueInfo;

        }
        catch (Exception e) {
            log.debug("Connection to ActiveMQ is unavailable.", e);
            return null;
        }
        finally {
            if (factory != null) {
                factory.close();
            }
            if (locator != null) {
                locator.close();
            }
        }
    }

    private String getMessageId(ClientSession session, String queueName) throws ActiveMQException {
        try {
            ClientConsumer consumer = session.createConsumer(queueName, true);
            ClientMessage clientMsg = consumer.receive(2000);
            return String.valueOf(clientMsg.getMessageID());
        }
        catch (Exception exp) {
            log.error("Unable to get message from queue {}", queueName);
            throw exp;
        }
    }

}



