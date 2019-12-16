/**
 * Copyright (c) 2009 - 2018 Red Hat, Inc.
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

import org.candlepin.audit.QpidConnection;
import org.candlepin.audit.QpidQmf;
import org.candlepin.audit.QpidStatus;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;

import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 *  A process that checks the current state of Qpid and notifies any listeners
 *  when the Qpid state has changed.
 */
@Singleton
public class QpidStatusMonitor implements Runnable {

    private static Logger log = LoggerFactory.getLogger(QpidStatusMonitor.class);

    private Configuration config;
    private ScheduledExecutorService executorService;
    private QpidConnection qpidConnection;
    private QpidQmf qmf;
    private List<QpidStatusListener> listeners;
    private QpidStatus lastStatus;
    private int delay;

    @Inject
    public QpidStatusMonitor(Configuration config, ScheduledExecutorService execService) {
        this.config = config;
        this.executorService = execService;
        this.listeners = new LinkedList<QpidStatusListener>();

        delay = config.getInt(ConfigProperties.QPID_MODE_TRANSITIONER_DELAY);
        if (delay < 1) {
            int defaultDelay = Integer.parseInt(
                ConfigProperties.DEFAULT_PROPERTIES.get(ConfigProperties.QPID_MODE_TRANSITIONER_DELAY));
            log.warn("{} is an invalid delay setting. Must be greater than 0. Defaulting to {}", delay,
                defaultDelay);
            delay = defaultDelay;
        }
        this.lastStatus = QpidStatus.UNKNOWN;
    }

    /**
     * Other dependencies are injected using method injection so
     * that Guice can handle circular dependencies
     */
    @Inject
    public void setQmf(QpidQmf qmf) {
        this.qmf = qmf;
    }

    @Inject
    public void setQpidConnection(QpidConnection qpidConnection) {
        this.qpidConnection = qpidConnection;
    }

    /**
     * Executes a single monitoring check.
     */
    @Override
    public void run() {
        try {
            log.debug("Executing Qpid status check...");
            monitor();
        }
        finally {
            log.debug("Next check will be in {} seconds.", delay);
        }
    }

    /**
     * Adds a listener that will be notified when the connection status changes.
     *
     * @param listener the listener to be added.
     */
    public void addStatusChangeListener(QpidStatusListener listener) {
        this.listeners.add(listener);
    }

    /**
     * Starts/schedules the connection monitoring process. The check will be run at a configured
     * interval.
     *
     * @see ConfigProperties
     */
    public void schedule() {
        log.info("Starting Qpid Status Monitor. Checks will be performed every {} seconds.", delay);
        executorService.scheduleWithFixedDelay(this, 10, delay, TimeUnit.SECONDS);
    }

    /**
     * Monitors the Qpid connection and notifies all listeners when the connection status changes.
     * If a listener throws an exception, the exception is logged and the next listener is notified
     * in turn.
     *
     * This method should handle all exceptions to ensure that the executor service continues to
     * schedule the periodic job.
     */
    private synchronized void monitor() {
        QpidStatus status;
        try {
            status = qmf.getStatus();
        }
        catch (Throwable t) {
            log.error("Error while executing status monitoring check", t);
            /*
             * Nothing more we can do here, since this is scheduled thread. We must
             * hope that this error won't infinitely recur with each scheduled execution
             */
            return;
        }

        log.debug("Qpid connection status - Old: {} New: {}", this.lastStatus, status);
        notifyListeners(this.lastStatus, status);

        this.lastStatus = status;
    }

    private void notifyListeners(QpidStatus oldStatus, QpidStatus newStatus) {
        for (QpidStatusListener listener : this.listeners) {
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
}
