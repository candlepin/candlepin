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
package org.fedoraproject.candlepin.pinsetter.tasks;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.JobListener;

/**
 * RefreshPoolsJobListener
 */
public class RefreshPoolsJobListener implements JobListener {

    @Override
    public String getName() {
        return "refresh jobs listener";
    }

    @Override
    public void jobToBeExecuted(JobExecutionContext context) {
        // TODO Auto-generated method stub
        if (context.getJobDetail().getJobClass().equals(RefreshPoolsJob.class)) {
            
        }

    }

    @Override
    public void jobExecutionVetoed(JobExecutionContext context) {
        // TODO Auto-generated method stub

    }

    @Override
    public void jobWasExecuted(JobExecutionContext context,
        JobExecutionException jobException) {
        // Upon completion look to see if there are any pending jobs, if so,
        // unpause the first one.
        if (context.getJobDetail().getJobClass().equals(RefreshPoolsJob.class)) {
            // find first pending job, then unpause it.
        }
    }

}
