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

import org.candlepin.audit.ActiveMQStatus;
import org.candlepin.common.config.Configuration;
import org.candlepin.config.ConfigProperties;

import org.apache.activemq.artemis.api.core.client.ActiveMQClient;
import org.apache.activemq.artemis.api.core.client.ClientSessionFactory;
import org.apache.activemq.artemis.api.core.client.ServerLocator;
import org.apache.activemq.artemis.core.remoting.CloseListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.Closeable;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Monitors the status of the ActiveMQ connection and notifies listeners when
 * there are issues with the connection.
 */
// FIXME Need to have this class hold its own connection for monitoring or we need
//       to hook into the ActiveMQSessionFactory's connection some how.
//@Singleton
@Component
public class ActiveMQStatusMonitor implements Closeable, Runnable, CloseListener {

    private static Logger log = LoggerFactory.getLogger(ActiveMQStatusMonitor.class);

    private ServerLocator locator;
    private ClientSessionFactory clientSessionFactory;

    private Configuration config;
    private long monitorInterval;
    private List<ActiveMQStatusListener> registeredListeners;
    private ActiveMQStatus lastReported;
    private ScheduledExecutorService executorService;
    private Future<?> future = null;

    // Assume that the initial connection is not available until
    // it is tested.
    protected boolean connectionOk = false;

    //@Inject
    @Autowired
    public ActiveMQStatusMonitor(Configuration config) throws Exception {
        this.config = config;
        this.monitorInterval = config.getLong(ConfigProperties.ACTIVEMQ_CONNECTION_MONITOR_INTERVAL);
        this.registeredListeners = new LinkedList<>();
        executorService = Executors.newSingleThreadScheduledExecutor();
        this.lastReported = ActiveMQStatus.UNKNOWN;
        initializeLocator();
    }

    // Protected for testing purposes.
    protected void initializeLocator() throws Exception {
        locator = ActiveMQClient.createServerLocator(generateServerUrl());
    }

    private String generateServerUrl() {
        StringBuilder serverUrlBuilder =
            new StringBuilder(this.config.getProperty(ConfigProperties.ACTIVEMQ_BROKER_URL));

        // TODO: Change this to use ACTIVEMQ_EMBEDDED_BROKER once configuration upgrades are in
        // place
        boolean embedded = this.config.getBoolean(ConfigProperties.ACTIVEMQ_EMBEDDED);

        if (!embedded) {
            serverUrlBuilder.append("?sslEnabled=true")
                .append("&trustStorePath=")
                .append(this.config.getProperty(ConfigProperties.ACTIVEMQ_TRUSTSTORE))
                .append("&trustStorePassword=")
                .append(this.config.getProperty(ConfigProperties.ACTIVEMQ_TRUSTSTORE_PASSWORD))
                .append("&keyStorePath=")
                .append(this.config.getProperty(ConfigProperties.ACTIVEMQ_KEYSTORE))
                .append("&keyStorePassword=")
                .append(this.config.getProperty(ConfigProperties.ACTIVEMQ_KEYSTORE_PASSWORD));
        }

        return serverUrlBuilder.toString();
    }

    /**
     * Checks the status of the connection to the broker and starts the monitor
     * if it is down.
     */
    public void initialize() {
        ActiveMQStatus current = testConnection() ? ActiveMQStatus.CONNECTED : ActiveMQStatus.DOWN;
        notifyListeners(current);

        // If the connection is currently down, start monitoring the connection
        // so that candlepin will be notified when it comes back up.
        if (ActiveMQStatus.DOWN.equals(current)) {
            monitorConnection();
        }
    }

    public void registerListener(ActiveMQStatusListener listener) {
        this.registeredListeners.add(listener);
    }

    @Override
    public void connectionClosed() {
        connectionOk = false;
        notifyListeners(ActiveMQStatus.DOWN);
        monitorConnection();
    }

    private synchronized void monitorConnection() {
        // Since there can be multiple connections reporting that they have closed,
        // ensure that we only start one monitoring task.
        if (future == null || future.isDone() || future.isCancelled()) {
            log.info("Scheduling connection retries.");
            future = executorService.scheduleAtFixedRate(this, 1, monitorInterval, TimeUnit.MILLISECONDS);
        }
        else {
            log.info("Monitor already running.");
        }
    }

    @Override
    public void run() {
        log.debug("Checking status of the ActiveMQ broker.");
        if (testConnection()) {
            notifyListeners(ActiveMQStatus.CONNECTED);
            future.cancel(false);
        }
    }


    protected synchronized boolean testConnection() {
        if (!connectionOk) {
            try {
                clientSessionFactory = locator.createSessionFactory();
                clientSessionFactory.getConnection().addCloseListener(this);
                log.info("Connection to ActiveMQ is available.");
                connectionOk = true;
            }
            catch (Exception e) {
                log.debug("Connection to ActiveMQ is unavailable.", e);
                connectionOk = false;
            }
        }

        return connectionOk;
    }

    private void notifyListeners(ActiveMQStatus newStatus) {
        log.debug("Notifying listeners of new status: {}", newStatus);
        this.registeredListeners.forEach(listener -> {
            try {
                listener.onStatusUpdate(lastReported, newStatus);
            }
            catch (Exception e) {
                // If the listener throws an exception, log it and move on to the next.
                log.error("Unable to notify listener about new status: {}", listener.getClass(), e);
            }
        });
        lastReported = newStatus;
    }

    @Override
    public void close() throws IOException {

        if (this.clientSessionFactory != null) {
            this.clientSessionFactory.close();
        }
        if (this.locator != null) {
            this.locator.close();
        }

        if (future != null) {
            future.cancel(false);
        }
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(10, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        }
        catch (InterruptedException e) {
            e.printStackTrace();
        }
    }
}
