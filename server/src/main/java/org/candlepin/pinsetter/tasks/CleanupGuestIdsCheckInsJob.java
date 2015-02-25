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
package org.candlepin.pinsetter.tasks;

import org.candlepin.model.GuestIdsCheckInCurator;

import com.google.inject.Inject;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Cleans up all but the most recent checkins for all consumers.
 */
public class CleanupGuestIdsCheckInsJob extends KingpinJob {

    // Every 8 hours:
    public static final String DEFAULT_SCHEDULE = "0 0 0/8 * * ?";

    private GuestIdsCheckInCurator guestIdsCheckInCurator;

    private static Logger log = LoggerFactory.getLogger(CleanupCheckInsJob.class);

    @Inject
    public CleanupGuestIdsCheckInsJob(GuestIdsCheckInCurator guestIdsCheckInCurator) {
        this.guestIdsCheckInCurator = guestIdsCheckInCurator;
    }

    public void toExecute(JobExecutionContext ctx) throws JobExecutionException {
        log.info("Cleaning up old consumer gueest ids check-ins.");
        int deleted = guestIdsCheckInCurator.cleanupOldCheckIns();
        log.info("Deleted {} guest ids check-ins.", deleted);
    }
}
