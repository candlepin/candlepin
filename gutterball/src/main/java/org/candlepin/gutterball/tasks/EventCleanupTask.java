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
import org.candlepin.common.config.ConfigurationException;
import org.candlepin.gutterball.config.ConfigProperties;
import org.candlepin.gutterball.curator.EventCurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.persist.UnitOfWork;



// Impl note:
// Currently this task exists outside of the Quartz/pinsetter tooling that CP uses. At the time of
// writing, this is the only periodic task in GB and, as such, is probably not worth migrating the
// afore mentioned pinsetter stuff up to common (and all that comes with it -- db changes, deps,
// etc.).
// If we start adding more tasks in the future, it will be worth investigating whether or not GB has
// a need for pinsetter proper.

/**
 * An EventCleanupTask is run periodically to clean up old events that are no longer needed by
 * gutterball. Only events that are NOT in the RECEIVED state are removed, as the data they
 * contain has already been processed. Events that are in the RECEIVED state are events that
 * have not yet been processed OR were not able to be processed.
 *
 * This task is configurable via the gutterball.conf file. See {@link ConfigProperties} for
 * more details.
 *
 * <pre>
 *      gutterball.tasks.event_cleanup.enabled:
 *          whether or not the event_cleanup tasks is enabled (default: false)
 *      gutterball.tasks.event_cleanup.interval:
 *          the interval (in interval_units) at which run this task (default: 24)
 *      gutterball.tasks.event_cleanup.interval_units:
 *          the unit of time (defined by {@link TimeUnit}) for event_cleanup.interval_units
 *          (default: hours)
 *      gutterball.tasks.event_cleanup.max_age
 *          the age (in max_age_units) at which events will be pruned (default: 24)
 *      gutterball.tasks.event_cleanup.max_age_units:
 *          the unit of time (defined by {@link TimeUnit} for event_cleanup.max_age
 *          (default: hours)
 * </pre>
 */
public class EventCleanupTask implements Runnable {
    private static Logger log = LoggerFactory.getLogger(EventCleanupTask.class);

    private ScheduledExecutorService service;
    private ScheduledFuture<?> scheduled;

    private UnitOfWork uow;

    private EventCurator eventCurator;

    private int maxEventAge;
    private TimeUnit maxEventAgeUnits;

    @Inject
    public EventCleanupTask(Configuration config, EventCurator eventCurator, UnitOfWork uow)
        throws ConfigurationException {

        this.eventCurator = eventCurator;
        this.uow = uow;

        boolean enabled = config.getBoolean(ConfigProperties.EVENT_CLEANUP_TASK_ENABLED, false);

        int interval = config.getInt(ConfigProperties.EVENT_CLEANUP_TASK_INTERVAL, 24);
        TimeUnit intervalUnits = this.timeUnitFromString(
            config.getString(ConfigProperties.EVENT_CLEANUP_TASK_INTERVAL_UNIT, "hours")
        );

        this.maxEventAge = config.getInt(ConfigProperties.EVENT_CLEANUP_TASK_MAX_EVENT_AGE, 24);
        this.maxEventAgeUnits = this.timeUnitFromString(
            config.getString(ConfigProperties.EVENT_CLEANUP_TASK_MAX_EVENT_AGE_UNIT, "hours")
        );

        long minutes = this.maxEventAgeUnits.toMinutes(this.maxEventAge);
        if (minutes > Integer.MAX_VALUE || minutes < 0) {
            throw new ConfigurationException(String.format(
                "Invalid max event age: %s %s",
                this.maxEventAge, this.maxEventAgeUnits.toString().toLowerCase()
            ));
        }

        if (enabled && interval > 0 && maxEventAge > 0) {
            log.info("Event Cleanup Task -- [Interval: {} {}, Max Event Age: {} {}]",
                interval, intervalUnits.toString().toLowerCase(), this.maxEventAge,
                this.maxEventAgeUnits.toString().toLowerCase()
            );

            service = Executors.newSingleThreadScheduledExecutor();
            scheduled = service.scheduleAtFixedRate(this, interval, interval, intervalUnits);
        }
    }

    public void shutdown() {
        log.info("Shutting down event cleanup task");

        if (this.service != null) {
            service.shutdownNow();
        }

        if (this.scheduled != null) {
            scheduled.cancel(true);
        }
    }

    @Override
    public void run() {
        log.info("Starting event cleanup");

        try {
            int minutes = (int) this.maxEventAgeUnits.toMinutes(this.maxEventAge);

            uow.begin();
            int cleaned = eventCurator.cleanupEvents(minutes);
            log.info("Cleaned up {} events.", cleaned);
        }
        catch (Exception e) {
            log.error("Could not clean up events.", e);
        }
        finally {
            uow.end();
        }
    }

    private TimeUnit timeUnitFromString(String unit) {
        try {
            // Null and empty values will be caught by various exceptions here
            return TimeUnit.valueOf(unit.toUpperCase());
        }
        catch (Exception e) {
            return TimeUnit.SECONDS;
        }
    }

}
