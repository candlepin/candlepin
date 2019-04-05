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
package org.candlepin.async.temp;

import org.candlepin.async.AsyncJob;
import org.candlepin.async.JobExecutionContext;
import org.candlepin.async.JobExecutionException;
import org.candlepin.async.JobManager;
import org.candlepin.model.Owner;
import org.candlepin.model.OwnerCurator;

import com.google.inject.Inject;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.Collection;


/**
 * A basic AsyncJob implementation that we can build out to manually test the
 * async job framework before we start to implement real job implementations.
 */
public class TestJob1 implements AsyncJob {

    private static Logger log = LoggerFactory.getLogger(TestJob1.class);
    private static final String JOB_KEY = "TEST_JOB1";

    private OwnerCurator ownerCurator;

    @Inject
    public TestJob1(OwnerCurator ownerCurator) {
        this.ownerCurator = ownerCurator;
    }


    // Register the job with the JobManager
    static {
        JobManager.registerJob(JOB_KEY, TestJob1.class);
    }

    // NOTE: In order the get the above static block to run when candlepin is initialized,
    //       we need to wrap the static key access in this method. When 'static final'
    //       is used, the static block isn't run due to the way java processes them.
    //
    //       'public static String' can be used but checkstyle warns against this.
    // TODO Are we Ok with this?
    public static String getJobKey() {
        return JOB_KEY;
    }

    @Override
    public String execute(JobExecutionContext jdata) throws JobExecutionException {
        // TODO Finish this implementation once we work on the bits that actually execute the job.
        log.info("TestJob1 has been started!");

        log.trace("this is a trace line");
        log.debug("this is a debug line");
        log.info("this is an info line");
        log.warn("this is a warning line");
        log.error("this is an error line");

        log.info("FETCHING OWNER INFO!");
        Collection<Owner> owners = this.ownerCurator.listAll().list();
        log.info("OWNERS: {}", owners);


        final boolean forceFailure = jdata.getJobData().getAsBoolean("force_failure");
        final boolean sleep = jdata.getJobData().getAsBoolean("sleep");

        if (sleep) {
            sleep();
        }

        if (forceFailure) {
            throw new JobExecutionException("Forced failure!");
        }

        log.info("TestJob1 has been executed!");
        return null;
    }

    private void sleep() {
        try {
            TimeUnit.SECONDS.sleep(10);
        }
        catch (Exception e) {
            log.info("TestJob1 has been interrupted!");
            Thread.currentThread().interrupt();
        }
    }
}
