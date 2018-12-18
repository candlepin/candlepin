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

import static org.mockito.Mockito.*;

import org.candlepin.model.JobCurator;

import org.junit.Before;
import org.junit.Test;

import org.quartz.JobExecutionContext;

import java.util.Date;


/**
 * JobCleanerTest
 */
public class JobCleanerTest extends BaseJobTest {

    @Before
    public void init() {
        super.init();
    }

    @Test
    public void execute() throws Exception {
        JobCurator curator = mock(JobCurator.class);
        JobCleaner cleaner = new JobCleaner(curator);
        JobExecutionContext context = mock(JobExecutionContext.class);
        injector.injectMembers(cleaner);

        cleaner.execute(context);
        verify(curator).cleanUpOldCompletedJobs(any(Date.class));
        verify(curator).cleanupAllOldJobs(any(Date.class));
    }
}
