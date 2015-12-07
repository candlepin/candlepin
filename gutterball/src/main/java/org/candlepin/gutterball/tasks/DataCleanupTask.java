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

import org.candlepin.common.config.Configuration;
import org.candlepin.common.config.ConfigurationException;
import org.candlepin.gutterball.config.ConfigProperties;
import org.candlepin.gutterball.curator.ComplianceSnapshotCurator;
import org.candlepin.gutterball.curator.EventCurator;
import org.candlepin.gutterball.util.cron.CronSchedule;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.inject.Inject;
import com.google.inject.persist.UnitOfWork;

import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;



/**
 * An DataCleanupTask is run periodically to clean up old events and data in Gutterball. Only events
 * that are NOT in the RECEIVED state are removed, as the data they contain has already been
 * processed. Events that are in the RECEIVED state are events that have not yet been processed OR
 * were not able to be processed.
 *
 * This task is configurable via the gutterball.conf file. See {@link ConfigProperties} for
 * more details.
 *
 * <pre>
 *      gutterball.tasks.data_cleanup.enabled:
 *          whether or not the data cleanup task is enabled (default: false)
 *      gutterball.tasks.data_cleanup.schedule:
 *          the schedule to use for running the data cleanup task (default: 0 3 * * *)
 *      gutterball.tasks.data_cleanup.max_data_age
 *          the age (in max_age_units) at which data will be pruned (default: 30)
 *      gutterball.tasks.data_cleanup.max_data_age_units:
 *          the unit of time (defined by {@link TimeUnit}) for data_cleanup.max_data_age
 *          (default: days)
 * </pre>
 */
public class DataCleanupTask implements Runnable {
    private static Logger log = LoggerFactory.getLogger(DataCleanupTask.class);

    private ScheduledExecutorService service;
    private ScheduledFuture<?> scheduledTask;
    private CronSchedule schedule;

    private ComplianceSnapshotCurator complianceSnapshotCurator;
    private EventCurator eventCurator;

    private UnitOfWork uow;

    private int maxEventAge;
    private TimeUnit maxEventAgeUnits;

    @Inject
    public DataCleanupTask(Configuration config, ComplianceSnapshotCurator complianceSnapshotCurator,
        EventCurator eventCurator, UnitOfWork uow) throws ConfigurationException {

        this.complianceSnapshotCurator = complianceSnapshotCurator;
        this.eventCurator = eventCurator;

        this.uow = uow;

        boolean enabled = config.getBoolean(ConfigProperties.DATA_CLEANUP_TASK_ENABLED, false);
        String schedule = config.getString(ConfigProperties.DATA_CLEANUP_TASK_SCHEDULE, null);

        if (enabled && schedule != null) {
            this.schedule = new CronSchedule(schedule);
            this.service = Executors.newSingleThreadScheduledExecutor();

            this.maxEventAge = config.getInt(ConfigProperties.DATA_CLEANUP_TASK_MAX_EVENT_AGE, 24);
            this.maxEventAgeUnits = this.timeUnitFromString(
                config.getString(ConfigProperties.DATA_CLEANUP_TASK_MAX_EVENT_AGE_UNIT, "hours")
            );

            long minutes = this.maxEventAgeUnits.toMinutes(this.maxEventAge);
            if (minutes > Integer.MAX_VALUE || minutes < 1) {
                throw new ConfigurationException(String.format(
                    "Invalid max data age: %s %s",
                    this.maxEventAge, this.maxEventAgeUnits.toString().toLowerCase()
                ));
            }

            this.reschedule();
        }
    }

    public void shutdown() {
        log.info("Shutting down data cleanup task");

        if (this.scheduledTask != null) {
            this.scheduledTask.cancel(true);
        }

        if (this.service != null) {
            this.service.shutdownNow();
        }
    }

    @Override
    public void run() {
        log.info("Running data cleanup task");

        try {
            int minutes = (int) this.maxEventAgeUnits.toMinutes(this.maxEventAge);

            // Delete expired compliance snapshots
            //  - Compliance objects affect:
            //      gb_compliance_snap
            //      gb_compliance_status_snap
            //      gb_compliance_reason_snap
            //      gb_reason_attr_snap
            //      gb_compprod_snap
            //      gb_partcompprod_snap
            //      gb_noncompprod_snap
            //      gb_partialstack_snap
            //      gb_entitlement_snap
            //      gb_ent_attr_snap
            //      gb_ent_der_prod_attr_snap
            //      gb_ent_der_prov_prod_snap
            //      gb_ent_prov_prod_snap
            //
            //  - Orphaned consumers are deleted during the deletion of compliances
            //      gb_consumer_snap
            //      gb_consumer_state
            //      gb_consumer_type_snap
            //      gb_consumer_facts_snap
            //      gb_installed_product_snap
            //      gb_consumer_guest_snap
            //      gb_consumer_guest_attributes
            //
            //  - Orphaned owners are deleted during the deletion of consumers
            //      gb_owner_snap
            //
            // Delete expired events
            //      gb_event

            uow.begin();
            int compliances = this.complianceSnapshotCurator.cleanupCompliances(minutes);
            int events = this.eventCurator.cleanupEvents(minutes);

            log.info("Cleaned up {} compliance snapshots and {} events.", compliances, events);
        }
        catch (Exception e) {
            log.error("Could not clean up expired data.", e);
        }
        finally {
            uow.end();
            this.reschedule();
        }
    }

    private void reschedule() {
        if (this.service == null) {
            throw new IllegalStateException("Executor service is currently unavailable");
        }

        long delay = 0;

        if (this.scheduledTask != null) {
            // Impl note:
            // At the time of writing, the only time this is called is when the task is initially
            // created and when the task is finished and is rescheduling itself. As such, we should
            // never be rescheduling while the task itself is active. We're only doing the bit of
            // cleanup as an additional sanity/cleanup step.

            this.scheduledTask.cancel(false);


            // Impl note:
            // Our scheduler has granularity to minutes, so if we finish quickly enough, we could end
            // up scheduling another run immediately. We add one minute to our current time to work
            // around this.
            delay = 60000;
        }

        long now = System.currentTimeMillis();
        Date nmin = new Date(now + delay);
        Date next = this.schedule.getNextOccurance(nmin);
        long diff = next.getTime() - now;

        this.scheduledTask = this.service.schedule(this, diff, TimeUnit.MILLISECONDS);

        log.info("Data cleanup task rescheduled. Next run: {}, Max Data Age: {} {}",
            next, this.maxEventAge, this.maxEventAgeUnits.toString().toLowerCase()
        );
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
