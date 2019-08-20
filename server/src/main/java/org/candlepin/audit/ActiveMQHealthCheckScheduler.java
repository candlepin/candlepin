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

import com.google.inject.Inject;
import org.candlepin.common.config.Configuration;
import org.candlepin.controller.ScheduledExecutorServiceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 *
 */
public class ActiveMQHealthCheckScheduler {
    private static Logger log = LoggerFactory.getLogger(ActiveMQHealthCheckScheduler.class);
    private Configuration config;
    private EventSinkConnection connection;

    @Inject
    public ActiveMQHealthCheckScheduler(Configuration config, EventSinkConnection connection) {
        this.config = config;
        this.connection = connection;
    }


    public void healthCheckScheduler() {
        log.info("ActiveMq Health Check invoked");
        ScheduledExecutorServiceProvider schdExc = new ScheduledExecutorServiceProvider();
        ScheduledExecutorService service = schdExc.get();
        ScheduledFuture<HashMap<String, QueueStatus>> firstExecution =
            service.schedule(new ActiveMQHealthCheck(this.config, this.connection), 0, TimeUnit.SECONDS);
        ScheduledFuture<HashMap<String, QueueStatus>> secondExecution =
            service.schedule(new ActiveMQHealthCheck(this.config, this.connection), 2000, TimeUnit.SECONDS);
        //TODO
    }

    private List<String> getAllQueue() {
        List<String> queueNames = ActiveMQContextListener.getActiveMQListeners(config);
        queueNames.replaceAll(s -> "event." + s);
        return queueNames;
    }

    public boolean isHealthy() {
        //TODO
        return false;
    }

}
