/*
 * Copyright (c) 2009 - 2024 Red Hat, Inc.
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
package org.candlepin.async.tasks;

import java.util.Objects;

import javax.inject.Inject;

import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.model.OwnerCurator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// TODO: Add a description here
public class ClaimedOwnerConsumerDetectionJob implements AsyncJob {

    private static final Logger log = LoggerFactory.getLogger(ClaimedOwnerConsumerDetectionJob.class);

    public static final String JOB_KEY = "ClaimedOwnerConsumerDetectionJob";
    public static final String JOB_NAME = "Claimed Owner Consumer Detection";
    // TODO: Is this correct cron job format?
    // Every day at 1 pm
    public static final String DEFAULT_SCHEDULE = "0 0 13 * * ?";

    private OwnerCurator ownerCurator;

    @Inject
    public ClaimedOwnerConsumerDetectionJob(OwnerCurator ownerCurator) {
        this.ownerCurator = Objects.requireNonNull(ownerCurator);
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        log.debug("Starting claimed owner consumer detection.");


        log.debug("Claimed owner consumer detection completed.");
    }

}
