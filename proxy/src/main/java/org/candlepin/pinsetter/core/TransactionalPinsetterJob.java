/**
 * Copyright (c) 2009 Red Hat, Inc.
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
package org.candlepin.pinsetter.core;

import com.wideplay.warp.persist.WorkManager;

import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;

/**
 * TransactionalPinsetterJob - wrapper to execute our pinsetter jobs in a db
 * unit of work only as big as a single job execution, avoiding the caching
 * we'd have from the app's default http request scope.
 *
 * A System principal is also provided, for event emission
 */
class TransactionalPinsetterJob implements Job {

    private WorkManager workManager;
    private Job wrappedJob;

    TransactionalPinsetterJob(Job wrappedJob, WorkManager workManager) {
        this.wrappedJob = wrappedJob;
        this.workManager = workManager;
    }

    @Override
    public void execute(JobExecutionContext context) throws JobExecutionException {
        /*
         * Execute our 'real' job inside a custom unit of work scope, instead
         * of the guice provided one, which is http request scoped.
         */
        workManager.beginWork();
        try {
            wrappedJob.execute(context);
        }
        finally {
            workManager.endWork();
        }
    }

    // For testing
    Job getWrappedJob() {
        return wrappedJob;
    }
}
