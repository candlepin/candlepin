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

package org.candlepin.gutterball.tasks;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import org.candlepin.common.config.Configuration;
import org.candlepin.gutterball.config.ConfigProperties;
import org.candlepin.gutterball.curator.EventCurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.persist.UnitOfWork;

/**
 * An EventCleanupTask is run periodically to clean up old events that are no longer needed by
 * gutterball. Only events that are NOT in the RECEIVED state are removed, as the data they
 * contain has already been processed. Events that are in the RECEIVED state are events that
 * have not yet been processed OR were not able to be processed.
 *
 * This task is configurable via the gutterball.conf file. See {@link ConfigProperties} for
 * more details.
 *
 * <PRE>
 *     gutterball.tasks.event_cleanup.unit:
 *         the unit of time defined by {@link TimeUnit} (default: hours)
 *     gutterball.tasks.event_cleanup.interval:
 *         the number of 'units' to rerun this task (default: 24)
 *     gutterball.tasks.event_cleanup.max_age_in_minutes:
 *         the max age, in minutes, of an event before it is deleted
 *         (default: 1440 minutes (24 hours))
 * </PRE>
 *
 */
public class EventCleanupTask implements Runnable {
    private static Logger log = LoggerFactory.getLogger(EventCleanupTask.class);

    private ScheduledExecutorService service;
    private ScheduledFuture<?> scheduled;

    private UnitOfWork uow;

    private EventCurator eventCurator;

    private int maxAgeOfEventInMinutes;

    @Inject
    public EventCleanupTask(Configuration config, EventCurator eventCurator, UnitOfWork uow) {
        this.eventCurator = eventCurator;
        this.uow = uow;
        service = Executors.newSingleThreadScheduledExecutor();

        int interval = config.getInt(ConfigProperties.EVENT_CLEANUP_TASK_INTERVAL);
        TimeUnit unit = timeUnitFromString(
            config.getString(ConfigProperties.EVENT_CLEANUP_TASK_INTERVAL_UNIT));
        maxAgeOfEventInMinutes = config.getInt(ConfigProperties.MAX_AGE_OF_EVENT_IN_MINUTES);

        log.info("Event Cleanup Task -- [interval: " + interval + " " + unit.toString().toLowerCase() +
            ", Max Event Age: " + maxAgeOfEventInMinutes + " minutes ]");
        scheduled = service.scheduleAtFixedRate(this, interval, interval, unit);
    }

    public void shutdown() {
        log.info("Shutting down event cleanup task");
        service.shutdownNow();
        scheduled.cancel(true);
    }

    @Override
    public void run() {
        log.info("Starting cleanup");
        try {
            uow.begin();
            int cleaned = eventCurator.cleanupEvents(maxAgeOfEventInMinutes);
            log.info("Cleaned up " + cleaned + " events.");
            uow.end();
        }
        catch (Exception e) {
            log.error("Could not clean up events.", e);
        }
        finally {
            uow.end();
        }
    }

    private TimeUnit timeUnitFromString(String unit) {
        TimeUnit timeUnit = TimeUnit.SECONDS;
        if (unit == null || unit.isEmpty()) {
            return timeUnit;
        }

        try {
            timeUnit = TimeUnit.valueOf(unit.toUpperCase());
        }
        catch (Exception e) {
            // Default to seconds.
        }
        return timeUnit;
    }

}
