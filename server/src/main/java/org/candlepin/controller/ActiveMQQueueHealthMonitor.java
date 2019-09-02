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
import org.candlepin.audit.ActiveMQStatus;
import org.candlepin.common.config.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 *  A process that checks the current state of ActiveMQ Queue health & notifies any listeners
 *  when the ActiveMQ broker state has changed.
 */
public class ActiveMQQueueHealthMonitor implements Runnable {
    private static Logger log = LoggerFactory.getLogger(ActiveMQQueueHealthMonitor.class);

    private List<ActiveMQQueueHealthListener> registeredListeners;
    private long monitorInterval;
    private ActiveMQStatus lastStatus;
    private ScheduledExecutorService executorService;
    private Configuration config;

    @Inject
    public ActiveMQQueueHealthMonitor(Configuration config, ScheduledExecutorService execService) {
        this.config = config;
        this.monitorInterval = 20;
        this.registeredListeners = new LinkedList<ActiveMQQueueHealthListener>();
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
    public void registerListener(ActiveMQQueueHealthListener listener) {
        this.registeredListeners.add(listener);
    }

    /**
     *
     */
    private void notifyListeners(ActiveMQStatus oldStatus, ActiveMQStatus newStatus) {
        for (ActiveMQQueueHealthListener listener : this.registeredListeners) {
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
        return ActiveMQStatus.UNHEALTHY;
    }

}
